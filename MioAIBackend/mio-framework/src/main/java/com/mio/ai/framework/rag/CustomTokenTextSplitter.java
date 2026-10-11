package com.mio.ai.framework.rag;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/3/29 10:48
 * @description: 自定义文本分割器
 */
public class CustomTokenTextSplitter extends TextSplitter {
    private static final int DEFAULT_CHUNK_SIZE = 800;

    private static final int DEFAULT_CHUNK_OVERLAP = 100;

    private static final int MIN_CHUNK_SIZE_CHARS = 350;

    private static final int MIN_CHUNK_LENGTH_TO_EMBED = 5;

    private static final int MAX_NUM_CHUNKS = 10000;

    private static final boolean KEEP_SEPARATOR = true;

    private final EncodingRegistry registry = Encodings.newLazyEncodingRegistry();

    private final Encoding encoding = this.registry.getEncoding(EncodingType.CL100K_BASE);

    // The target size of each text chunk in tokens
    private final int chunkSize;

    // Number of tokens the previous chunk overlaps into the next one, so that
    // sentences cut at the boundary stay retrievable from both sides
    private final int chunkOverlap;

    // The minimum size of each text chunk in characters
    private final int minChunkSizeChars;

    // Discard chunks shorter than this
    private final int minChunkLengthToEmbed;

    // The maximum number of chunks to generate from a text
    private final int maxNumChunks;

    private final boolean keepSeparator;

    public CustomTokenTextSplitter() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP, MIN_CHUNK_SIZE_CHARS, MIN_CHUNK_LENGTH_TO_EMBED,
                MAX_NUM_CHUNKS, KEEP_SEPARATOR);
    }

    public CustomTokenTextSplitter(boolean keepSeparator) {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP, MIN_CHUNK_SIZE_CHARS, MIN_CHUNK_LENGTH_TO_EMBED,
                MAX_NUM_CHUNKS, keepSeparator);
    }

    public CustomTokenTextSplitter(int chunkSize, int chunkOverlap, int minChunkSizeChars,
                                   int minChunkLengthToEmbed, int maxNumChunks, boolean keepSeparator) {
        this.chunkSize = chunkSize;
        // 重叠不能超过块大小的一半，否则相邻块内容大量重复且可能无法推进
        this.chunkOverlap = Math.max(0, Math.min(chunkOverlap, chunkSize / 2));
        this.minChunkSizeChars = minChunkSizeChars;
        this.minChunkLengthToEmbed = minChunkLengthToEmbed;
        this.maxNumChunks = maxNumChunks;
        this.keepSeparator = keepSeparator;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected List<String> splitText(String text) {
        return doSplit(text, this.chunkSize);
    }

    protected List<String> doSplit(String text, int chunkSize) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<Integer> tokens = getEncodedTokens(text);
        List<String> chunks = new ArrayList<>();
        int num_chunks = 0;
        while (!tokens.isEmpty() && num_chunks < this.maxNumChunks) {
            int take = Math.min(chunkSize, tokens.size());
            // 剩余量不超过重叠数时并入当前块，避免下一轮为尾巴再造一个几乎全重复的小块
            if (take < tokens.size() && tokens.size() - take <= this.chunkOverlap) {
                take = tokens.size();
            }
            List<Integer> chunk = tokens.subList(0, take);
            String chunkText = decodeTokens(chunk);

            // Skip the chunk if it is empty or whitespace
            if (chunkText.trim().isEmpty()) {
                tokens = tokens.subList(chunk.size(), tokens.size());
                continue;
            }

            // Find the last period or punctuation mark in the chunk
            int lastPunctuation = Math.max(chunkText.lastIndexOf('.'),
                    Math.max(chunkText.lastIndexOf('?'),
                    Math.max(chunkText.lastIndexOf('!'),
                    Math.max(chunkText.lastIndexOf('。'),
                    Math.max(chunkText.lastIndexOf('？'),
                    Math.max(chunkText.lastIndexOf('！'),
                        chunkText.lastIndexOf('\n')))))));

            if (lastPunctuation != -1 && lastPunctuation > this.minChunkSizeChars) {
                // Truncate the chunk text at the punctuation mark
                chunkText = chunkText.substring(0, lastPunctuation + 1);
            }

            String chunkTextToAppend = (this.keepSeparator) ? chunkText.trim()
                    : chunkText.replace(System.lineSeparator(), " ").trim();
            if (chunkTextToAppend.length() > this.minChunkLengthToEmbed) {
                chunks.add(chunkTextToAppend);
            }

            // Advance past the consumed tokens, keeping chunkOverlap tokens as the head of the next chunk
            int consumed = getEncodedTokens(chunkText).size();
            if (consumed >= tokens.size()) {
                // 全部消费：直接 break 会让循环外的"剩余 token"块把整段再追加一遍
                tokens = tokens.subList(tokens.size(), tokens.size());
            } else {
                int advance = Math.max(1, consumed - this.chunkOverlap);
                tokens = tokens.subList(advance, tokens.size());
            }

            num_chunks++;
        }

        // Handle the remaining tokens
        if (!tokens.isEmpty()) {
            String remaining_text = decodeTokens(tokens).replace(System.lineSeparator(), " ").trim();
            if (remaining_text.length() > this.minChunkLengthToEmbed) {
                chunks.add(remaining_text);
            }
        }

        return chunks;
    }

    private List<Integer> getEncodedTokens(String text) {
        Assert.notNull(text, "Text must not be null");
        return this.encoding.encode(text).boxed();
    }

    private String decodeTokens(List<Integer> tokens) {
        Assert.notNull(tokens, "Tokens must not be null");
        var tokensIntArray = new IntArrayList(tokens.size());
        tokens.forEach(tokensIntArray::add);
        return this.encoding.decode(tokensIntArray);
    }

    public static final class Builder {

        private int chunkSize = DEFAULT_CHUNK_SIZE;

        private int chunkOverlap = DEFAULT_CHUNK_OVERLAP;

        private int minChunkSizeChars = MIN_CHUNK_SIZE_CHARS;

        private int minChunkLengthToEmbed = MIN_CHUNK_LENGTH_TO_EMBED;

        private int maxNumChunks = MAX_NUM_CHUNKS;

        private boolean keepSeparator = KEEP_SEPARATOR;

        private Builder() {
        }

        public Builder withChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
            return this;
        }

        public Builder withChunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
            return this;
        }

        public Builder withMinChunkSizeChars(int minChunkSizeChars) {
            this.minChunkSizeChars = minChunkSizeChars;
            return this;
        }

        public Builder withMinChunkLengthToEmbed(int minChunkLengthToEmbed) {
            this.minChunkLengthToEmbed = minChunkLengthToEmbed;
            return this;
        }

        public Builder withMaxNumChunks(int maxNumChunks) {
            this.maxNumChunks = maxNumChunks;
            return this;
        }

        public Builder withKeepSeparator(boolean keepSeparator) {
            this.keepSeparator = keepSeparator;
            return this;
        }

        public CustomTokenTextSplitter build() {
            return new CustomTokenTextSplitter(this.chunkSize, this.chunkOverlap, this.minChunkSizeChars,
                    this.minChunkLengthToEmbed, this.maxNumChunks, this.keepSeparator);
        }

    }
}