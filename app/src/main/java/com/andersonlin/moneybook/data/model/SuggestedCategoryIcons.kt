package com.andersonlin.moneybook.data.model

/** 只替换通用占位图标，保留用户自行选择的图标。 */
fun Category.withSuggestedIcon(): Category {
    if(icon != "💰") return this
    val suggested = mapOf(
        "餐饮" to "🍜", "交通" to "🚇", "购物" to "🛍️", "住宿" to "🏨", "娱乐" to "🎮", "医疗" to "💊",
        "日用" to "🧴", "零食" to "🍪", "宠物" to "🐱", "用车" to "🚗", "亲友" to "👪", "住房" to "🏠",
        "水果" to "🍎", "礼金" to "🎁", "数码" to "💻", "运动" to "🏸", "旅行" to "🧳", "服饰" to "👕",
        "文化" to "📚", "捐赠" to "🤲", "其他" to "📦", "社交" to "🤝", "学习" to "🎓",
        "工资" to "💼", "红包" to "🧧", "兼职" to "🛠️"
    )[name] ?: "📦"
    return copy(icon=suggested)
}
