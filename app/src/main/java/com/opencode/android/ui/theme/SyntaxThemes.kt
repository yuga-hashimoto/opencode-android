package com.opencode.android.ui.theme

import androidx.compose.ui.graphics.Color

data class SyntaxTheme(
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val function: Color,
    val type: Color,
    val variable: Color,
    val operator: Color
)

val OneDarkSyntax = SyntaxTheme(
    keyword = Color(0xFFC678DD),
    string = Color(0xFF98C379),
    comment = Color(0xFF5C6370),
    number = Color(0xFFD19A66),
    function = Color(0xFF61AFEF),
    type = Color(0xFFE5C07B),
    variable = Color(0xFFE06C75),
    operator = Color(0xFF56B6C2)
)

val MonokaiSyntax = SyntaxTheme(
    keyword = Color(0xFFF92672),
    string = Color(0xFFE6DB74),
    comment = Color(0xFF75715E),
    number = Color(0xFFAE81FF),
    function = Color(0xFFA6E22E),
    type = Color(0xFF66D9EF),
    variable = Color(0xFFFD971F),
    operator = Color(0xFFF92672)
)

val GitHubDarkSyntax = SyntaxTheme(
    keyword = Color(0xFFFF7B72),
    string = Color(0xFFA5D6FF),
    comment = Color(0xFF8B949E),
    number = Color(0xFF79C0FF),
    function = Color(0xFFD2A8FF),
    type = Color(0xFFFFA657),
    variable = Color(0xFFFFA198),
    operator = Color(0xFFFF7B72)
)

val SolarizedDarkSyntax = SyntaxTheme(
    keyword = Color(0xFF859900),
    string = Color(0xFF2AA198),
    comment = Color(0xFF586E75),
    number = Color(0xFFD33682),
    function = Color(0xFF268BD2),
    type = Color(0xFFB58900),
    variable = Color(0xFF268BD2),
    operator = Color(0xFF859900)
)

val syntaxThemes = mapOf(
    "one-dark" to OneDarkSyntax,
    "monokai" to MonokaiSyntax,
    "github-dark" to GitHubDarkSyntax,
    "solarized-dark" to SolarizedDarkSyntax
)

fun syntaxThemeFor(key: String?): SyntaxTheme =
    syntaxThemes[key] ?: OneDarkSyntax
