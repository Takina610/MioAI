package com.mio.ai.bot.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.mio.ai.bot.service.image.ImageResult;
import com.mio.ai.framework.zagent.tools.ToolEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * render_ui 工具：把模型产出的结构化 UI 块发布进回答流（动态交互式回答的产品侧出口）。
 * <p>模型按 inputSchema 产出 blocks 数组 → 逐块语义校验（{@link UiBlockValidator}，
 * 图片类块的 URL 须为本会话 image_search 结果）→ 新块经 {@link BotEventChannel#uiBlock} 下发，
 * 带 targetId 的块经 {@link BotEventChannel#uiBlockReplace} 原位替换既有块——
 * 非法块丢弃并在回执中说明原因，模型可自纠重调。回执携带每个块的稳定 id，
 * 模型后续轮次可凭 targetId 更新既有块而不重复创建。
 */
final class UiRenderTool {

    private static final int MAX_BLOCKS_PER_CALL = 8;
    private static final Pattern BLOCK_ID = Pattern.compile("^ui-[0-9a-f]{8}$");

    private static final String DESCRIPTION = """
            Render interactive UI blocks (cards, chart, table, tasks, form, actions, status, timeline, quote, \
            metric, choice, image, image_list, image_gallery, figure) inline in your chat answer, above your \
            closing text. Use it proactively: whenever your answer contains a multi-stage plan, a comparison of \
            options, numeric data worth visualizing, or real images from image_search, render it with this tool \
            instead of Markdown headings and tables. To update a block you rendered earlier, pass its targetId \
            (ids are listed in every render result) — the block updates in place instead of duplicating.""";

    /**
     * inputSchema 只约束 JSON 层（类型/枚举/上限），逐字段语义校验在 {@link UiBlockValidator}——
     * schema 保持扁平（无 oneOf），对弱模型更友好，坏块以回执告知而非整调用失败。
     * 注意：改动结构后务必以 JSON 解析器校验括号平衡——Spring AI 解析失败会静默丢弃参数
     * schema（模型只能瞎猜参数），注册期的 readTree 校验只在日志里提示。
     */
    private static final String SCHEMA = """
            {
            "type":"object",
            "properties":{
            "blocks":{
            "type":"array",
            "minItems":1,
            "maxItems":8,
            "description":"Ordered UI blocks rendered inline in your answer, above your closing text.",
            "items":{
            "type":"object",
            "properties":{
            "kind":{
            "type":"string",
            "enum":["cards","chart","table","tasks","form","actions","status","timeline","quote","metric","choice","image","image_list","image_gallery","figure"],
            "description":"Block type"
            },
            "targetId":{
            "type":"string",
            "pattern":"^ui-[0-9a-f]{8}$",
            "description":"Optional: id of an existing UI block to replace in place (ids are listed in previous render_ui results). Omit to create a new block."
            },
            "title":{
            "type":"string",
            "description":"Optional block title"
            },
            "cards":{
            "type":"array",
            "maxItems":6,
            "description":"kind=cards: stat/info cards",
            "items":{
            "type":"object",
            "properties":{
            "title":{"type":"string"},
            "value":{"type":"string","description":"Headline value (number or short stat)"},
            "desc":{"type":"string"},
            "trend":{"type":"string","enum":["up","down","flat"]}
            },
            "required":["title"]
            }
            },
            "chartType":{
            "type":"string",
            "enum":["bar","line","pie","scatter"],
            "description":"kind=chart only"
            },
            "categories":{
            "type":"array",
            "items":{"type":"string"},
            "description":"kind=chart: x-axis labels or pie slice names"
            },
            "series":{
            "type":"array",
            "maxItems":4,
            "description":"kind=chart: data series",
            "items":{
            "type":"object",
            "properties":{
            "name":{"type":"string"},
            "data":{"type":"array","items":{"type":"number"}}
            },
            "required":["name","data"]
            }
            },
            "columns":{
            "type":"array",
            "maxItems":8,
            "items":{"type":"string"},
            "description":"kind=table: column headers"
            },
            "rows":{
            "type":"array",
            "maxItems":12,
            "description":"kind=table: rows of cells (string/number)",
            "items":{"type":"array"}
            },
            "items":{
            "type":"array",
            "maxItems":20,
            "description":"kind=tasks (text/status) / kind=metric (label/value/unit/trend) / kind=image_list (url/title/desc/author/license) items",
            "items":{
            "type":"object",
            "properties":{
            "text":{"type":"string","description":"tasks item text"},
            "status":{"type":"string","enum":["todo","doing","done"]},
            "label":{"type":"string","description":"metric label or choice option label"},
            "value":{"type":"string","description":"metric headline value"},
            "unit":{"type":"string"},
            "trend":{"type":"string","enum":["up","down","flat"]},
            "url":{"type":"string","description":"image_list: https image url from image_search results (verbatim)"},
            "thumbnailUrl":{"type":"string"},
            "title":{"type":"string","description":"image_list row title"},
            "desc":{"type":"string","description":"image_list row description"},
            "sourceUrl":{"type":"string","description":"source page (https)"},
            "sourceName":{"type":"string"},
            "author":{"type":"string"},
            "license":{"type":"string"}
            }
            }
            },
            "fields":{
            "type":"array",
            "maxItems":12,
            "description":"kind=form: fields the user fills and submits back as a message",
            "items":{
            "type":"object",
            "properties":{
            "label":{"type":"string"},
            "type":{"type":"string","enum":["text","textarea","number","select"]},
            "options":{"type":"array","items":{"type":"string"},"description":"choices for type=select"},
            "placeholder":{"type":"string"},
            "required":{"type":"boolean"}
            },
            "required":["label"]
            }
            },
            "submitLabel":{"type":"string","description":"kind=form: submit button label"},
            "actions":{
            "type":"array",
            "maxItems":6,
            "description":"kind=actions: buttons that each send their prompt as the user's next message",
            "items":{
            "type":"object",
            "properties":{
            "label":{"type":"string"},
            "prompt":{"type":"string","description":"Message sent when the button is clicked"},
            "style":{"type":"string","enum":["primary","default"]}
            },
            "required":["label","prompt"]
            }
            },
            "steps":{
            "type":"array",
            "maxItems":12,
            "description":"kind=status: process step states",
            "items":{
            "type":"object",
            "properties":{
            "text":{"type":"string"},
            "status":{"type":"string","enum":["todo","doing","done","failed"]}
            },
            "required":["text"]
            }
            },
            "events":{
            "type":"array",
            "maxItems":20,
            "description":"kind=timeline: chronological events",
            "items":{
            "type":"object",
            "properties":{
            "time":{"type":"string","description":"Time or period label, e.g. 1997 / Q1 / 第一阶段"},
            "title":{"type":"string"},
            "desc":{"type":"string"}
            },
            "required":["title"]
            }
            },
            "text":{"type":"string","description":"kind=quote: the quoted passage"},
            "author":{"type":"string","description":"kind=quote attribution"},
            "source":{"type":"string","description":"kind=quote source (book/talk/link)"},
            "options":{
            "type":"array",
            "maxItems":6,
            "description":"kind=choice: options the user picks one of; clicking sends its prompt (or label)",
            "items":{
            "type":"object",
            "properties":{
            "label":{"type":"string"},
            "prompt":{"type":"string","description":"Message sent when the option is picked (defaults to the label)"}
            },
            "required":["label"]
            }
            },
            "image":{
            "type":"object",
            "description":"kind=image / kind=figure: single image (url must be an image_search result, verbatim)",
            "properties":{
            "url":{"type":"string","description":"https image url from image_search results"},
            "thumbnailUrl":{"type":"string"},
            "title":{"type":"string"},
            "alt":{"type":"string"},
            "caption":{"type":"string"},
            "sourceUrl":{"type":"string","description":"source page (https)"},
            "sourceName":{"type":"string"},
            "author":{"type":"string"},
            "license":{"type":"string"},
            "width":{"type":"number"},
            "height":{"type":"number"}
            },
            "required":["url"]
            },
            "images":{
            "type":"array",
            "maxItems":12,
            "description":"kind=image_gallery: image set (each url from image_search results, verbatim)",
            "items":{
            "type":"object",
            "properties":{
            "url":{"type":"string"},
            "thumbnailUrl":{"type":"string"},
            "title":{"type":"string"}
            },
            "required":["url"]
            }
            },
            "caption":{"type":"string","description":"kind=figure: caption explaining the image"},
            "ratio":{
            "type":"string",
            "enum":["auto","16/9","4/3","1/1","3/4"],
            "description":"kind=image/figure: aspect ratio (default auto by width/height)"
            },
            "fit":{
            "type":"string",
            "enum":["cover","contain"],
            "description":"kind=image/figure: object-fit (default cover)"
            }
            },
            "required":["kind"]
            }
            }
            },
            "required":["blocks"]
            }""";

    private UiRenderTool() {
    }

    /** 注册入口（MioBot 主轮次注册；子代理注册表不含）。imageResolver=本会话图片资产登记簿（url→元数据，未命中 null） */
    static ToolEntry entry(BotEventChannel channel, Function<String, ImageResult> imageResolver) {
        return ToolEntry.of("render_ui", DESCRIPTION, SCHEMA,
                (input, ctx) -> execute(input, channel, imageResolver));
    }

    private static String execute(JsonNode input, BotEventChannel channel, Function<String, ImageResult> imageResolver) {
        JsonNode rawBlocks = input == null ? null : input.get("blocks");
        if (rawBlocks == null || !rawBlocks.isArray() || rawBlocks.isEmpty()) {
            return "<tool_use_error>blocks must be a non-empty array of UI blocks.</tool_use_error>";
        }
        List<String> created = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int rendered = 0;
        int index = 0;
        for (JsonNode raw : rawBlocks) {
            index++;
            if (rendered >= MAX_BLOCKS_PER_CALL) {
                skipped.add("block[" + index + "] (over " + MAX_BLOCKS_PER_CALL + " blocks per call)");
                continue;
            }
            try {
                String targetId = targetIdOf(raw);
                Map<String, Object> block = UiBlockValidator.validateBlock(raw, imageResolver);
                if (targetId == null) {
                    created.add(block.get("kind") + " " + channel.uiBlock(block));
                } else if (channel.uiBlockReplace(targetId, block)) {
                    updated.add(block.get("kind") + " " + targetId);
                } else {
                    skipped.add("block[" + index + "] (targetId " + targetId
                            + " not found in this conversation; resend without targetId to create a new block)");
                    continue;
                }
                rendered++;
            } catch (IllegalArgumentException e) {
                skipped.add("block[" + index + "] (" + e.getMessage() + ")");
            }
        }
        if (created.isEmpty() && updated.isEmpty()) {
            return "<tool_use_error>No valid UI block in this call. Reasons: "
                    + String.join("; ", skipped) + " Fix the fields and call render_ui again.</tool_use_error>";
        }
        StringBuilder ack = new StringBuilder();
        if (!created.isEmpty()) {
            ack.append("Created block(s): ").append(String.join(", ", created)).append('.');
        }
        if (!updated.isEmpty()) {
            ack.append(" Updated in place: ").append(String.join(", ", updated)).append('.');
        }
        ack.append(" They render inline above your closing text; write the closing text next without repeating "
                + "their content. Block ids are stable — to revise a block in a later turn, pass its id as "
                + "targetId instead of rendering a duplicate.");
        if (!skipped.isEmpty()) {
            ack.append(" Skipped invalid: ").append(String.join("; ", skipped)).append('.');
        }
        return ack.toString();
    }

    /** targetId 格式校验：会话内块 id（ui- + 8 位十六进制）；格式非法按坏块自纠 */
    private static String targetIdOf(JsonNode raw) {
        JsonNode node = raw.get("targetId");
        if (node == null || node.isNull()) {
            return null;
        }
        String id = node.asText().trim();
        if (!BLOCK_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("targetId must match ui-xxxxxxxx (see ids in previous render results)");
        }
        return id;
    }
}
