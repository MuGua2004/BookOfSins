# Book of Sins (罪孽之书)

An accessory mod themed around the Seven Deadly Sins, for **Minecraft 1.20.1 (Forge)**.

一个以七原罪为主题的饰品模组，适用于 **Minecraft 1.20.1 (Forge)**。

When you first enter a world, a pitch-black tome — the *Root of Sin* — appears in your hand. Use it within 10 real minutes to choose your burden: equip up to **7 Sin Tomes**, each carrying **7 escalating curses** (49 in total). The deeper the sin, the greater the starting reward.

首次进入世界时，一本漆黑的册子《根源之罪》会出现在你手中——10 现实分钟内使用，选择你的背负：装备至多 7 本罪孽之书，每本带 7 条层层递进的诅咒（共 49 条）。背负越深，开局的回赠越强。

Purify each curse by fulfilling its condition with the **Reversal Tomes**, and claim the **Root Seals** — accessories engraved with a sin's reversed power. Special drops can be crafted into charm accessories.

用逆转之书完成各自的净化条件，洗净诅咒，获得对应的根源之印——封存着罪之反面的饰品。特殊掉落可合成护符。

## Requirements / 前置

| Mod | Version |
|---|---|
| [AC Core](https://www.curseforge.com/minecraft/mc-mods/ac-core) | 1.0.3+ |
| [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) | 5.x |

Optional: JEI (reverse tome info pages) / 可选：JEI（逆转之书信息页）

## Build / 构建

```bash
./gradlew build
```

Output: `build/libs/book_of_sins-<version>.jar`

- JDK 17 required / 需要 JDK 17
- The AC Core jar used at compile time is vendored in `libs/` (MIT).
- Curios / JEI are resolved automatically from their Maven repositories.

构建时使用的 AC Core jar 已随仓库附带（`libs/`，MIT 协议）；Curios / JEI 会自动从各自 Maven 仓库解析。

## License / 许可

[MIT](LICENSE)
