package de.cprohm.ktwasm.base

data class Table(val funcs: MutableList<FunctionRef?> = mutableListOf()) {
    override fun toString(): String = "Table([${funcs.size}])"

    fun clone(): Table = Table(funcs.toMutableList())
    fun getOrNull(idx: Int): FunctionRef? = funcs.getOrNull(idx)

    operator fun set(index: Int, value: FunctionRef?) {
        funcs[index] = value
    }
}