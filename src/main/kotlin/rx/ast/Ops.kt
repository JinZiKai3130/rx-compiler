package rx.ast

/** 二元运算符：+ - * / % << >> & | ^ && || == != < <= > >= */
enum class BinOp {
    ADD, SUB, MUL, DIV, MOD,             // 算术
    SHL, SHR, BIT_AND, BIT_OR, BIT_XOR,  // 位移与位运算
    AND, OR,                             // 逻辑（&& / ||）
    EQ, NE, LT, LE, GT, GE,              // 比较
}

/** 一元运算符：-x、!x、*x（解引用）。
 *  &x / &mut x 是单独的 ReferenceExprNode，因为它带 mut 标记。 */
enum class UnOp { NEG, NOT, DEREF }

/** 赋值运算符：= += -= *= /= %= &= |= ^= <<= >>=。
 *  注意：let 里的等号不属于它，那个在 LetNode 里。 */
enum class AssignOp {
    ASSIGN,
    ADD_ASSIGN, SUB_ASSIGN, MUL_ASSIGN, DIV_ASSIGN, MOD_ASSIGN,
    BIT_AND_ASSIGN, BIT_OR_ASSIGN, BIT_XOR_ASSIGN, SHL_ASSIGN, SHR_ASSIGN,
}
