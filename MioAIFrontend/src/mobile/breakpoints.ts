/**
 * 移动端/响应式断点常量（JS 侧单一事实来源）。
 *
 * 与 `styles/_tokens.scss` 的 $bp-* 一一对应；改这里必须同步改那里。
 * 全站不再散写媒体查询字面量：JS 依据这些常量在 <html> 上维护标记类，
 * 样式侧统一以 `html.<标记>` 前缀消费（见 styles/_tokens.scss 的说明）。
 */

/** 小屏手机上限：进一步压缩留白、精简工具栏文字 */
export const COMPACT_MAX_WIDTH = 480

/** 紧凑上限：首页大标题/横幅等需要更早折叠的场景（沿用既有 640px 语义） */
export const TIGHT_MAX_WIDTH = 640

/** 手机布局上限：≤ 此宽度进入移动外壳（侧栏抽屉化、内容满宽、表格横向滚动） */
export const MOBILE_MAX_WIDTH = 768

/** 窄屏上限：首页导航折叠为菜单按钮（与既有 900px 折叠语义对齐） */
export const TABLET_MAX_WIDTH = 960

/** 中小屏上限：图表/指标栅格收窄 */
export const NARROW_MAX_WIDTH = 1200

/** 触屏判定：主指针无 hover 能力（触屏设备的可靠信号，窄窗口的桌面鼠标不命中） */
export const TOUCH_QUERY = '(hover: none)'
