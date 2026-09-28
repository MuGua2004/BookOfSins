package com.example.book_of_sins.sins;

/**
 * 七宗罪典籍（罪孽之书）。
 *
 * <p>每本书 = 7 条诅咒；全部实现于 {@link SinCurseEvents}（代码侧持续监听），
 * 数据包定义（{@code data/book_of_sins/accessories/sin_tome_*.json}）目前为空效果，
 * 预留给未来接入 ac_core 链条系统。</p>
 *
 * <p>✅ 与 ac_core 资源系统联动：每装备一本，玩家获得 +10 罪孽（sin），
 * 供其他饰品的条件表达式（如 {@code sin > 16}）引用。</p>
 */
public enum SinTome {

    PRIDE("pride", "傲慢"),
    ENVY("envy", "嫉妒"),
    WRATH("wrath", "暴怒"),
    SLOTH("sloth", "懒惰"),
    GREED("greed", "贪婪"),
    GLUTTONY("gluttony", "暴食"),
    LUST("lust", "色欲");

    /** 每本书提供的罪孽资源量（一本书 7 宗罪 → 7 点）。 */
    public static final double SIN_PER_TOME = 7.0D;

    private final String id;
    private final String display;

    SinTome(String id, String display) {
        this.id = id;
        this.display = display;
    }

    /** 罪的英文短 id（pride/envy/...）。 */
    public String id() {
        return id;
    }

    /** 物品注册名后缀与文件 id 后缀（sin_tome_pride …）。 */
    public String itemId() {
        return "sin_tome_" + id;
    }

    public String display() {
        return display;
    }
}
