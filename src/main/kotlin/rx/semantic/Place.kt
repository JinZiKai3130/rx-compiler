package rx.semantic

import rx.ast.Type

// 用来确定当前是否可以进行修改
internal class Place(
    val type: Type,
    val mutable: Boolean,
    val underShared: Boolean, // 用来规避&&mut的情况
)