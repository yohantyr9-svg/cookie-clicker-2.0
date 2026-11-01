package com.cookieclicker.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.pow

/** Secret admin panel: tap the "Cookie Clicker" title 5 times, then enter this PIN. CHANGE IT! */
private const val ADMIN_PIN = "1234"

private val Brown = Color(0xFF2B1A12)
private val Cream = Color(0xFFFFF1DC)
private val Caramel = Color(0xFFE0A15C)
private val CardBrown = Color(0xFF45291B)
private val Muted = Color(0xFFCDB89A)
private const val COOKIE = "\uD83C\uDF6A"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bars = SystemBarStyle.dark(Brown.toArgb())
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Caramel, onPrimary = Brown,
                    background = Brown, onBackground = Cream,
                    surface = Brown, onSurface = Cream,
                    secondaryContainer = Color(0xFF5A3A27), onSecondaryContainer = Cream,
                    outline = Color(0xFF8A6A50),
                )
            ) { GameScreen() }
        }
    }
}

// ---------------------------------------------------------------- game data

private data class Producer(val name: String, val emoji: String, val baseCost: Double, val cps: Double)

private val producers = listOf(
    Producer("Cursor", "\uD83D\uDC46", 15.0, 0.1),
    Producer("Grandma", "\uD83D\uDC75", 100.0, 1.0),
    Producer("Farm", "\uD83C\uDF3E", 1_100.0, 8.0),
    Producer("Mine", "\u26CF\uFE0F", 12_000.0, 47.0),
    Producer("Factory", "\uD83C\uDFED", 130_000.0, 260.0),
    Producer("Bank", "\uD83C\uDFE6", 1_400_000.0, 1_400.0),
    Producer("Rocket", "\uD83D\uDE80", 20_000_000.0, 7_800.0),
)

/** kind 0 = doubles one baker, 1 = doubles click power, 2 = +10% all cookies per second. */
private class Upgrade(
    val id: Int, val name: String, val desc: String, val emoji: String,
    val cost: Double, val kind: Int, val target: Int, val req: Double,
)

private val tierNames = listOf("Sprinkles", "Choco Chips", "Butter Boost", "Golden Dough", "Secret Recipe",
    "Double Oven", "Triple Oven", "Diamond Sugar", "Cosmic Flour", "Infinite Batch")
private val tierReq = listOf(1, 10, 25, 50, 75, 100, 150, 200, 250, 300)
private val clickNames = listOf("Soft Gloves", "Iron Fingers", "Sugar Rush", "Quick Tap", "Turbo Thumb",
    "Golden Touch", "Rhythm Clicker", "Finger Gym", "Double Tap", "Lightning Hands",
    "Cookie Whisperer", "Mega Mitts", "Titan Thumbs", "Cosmic Fingers", "The God Hand")
private val globalNames = listOf("Baker's Union", "Vanilla Extract", "Cinnamon Swirl", "Milk Fountain", "Golden Spatula",
    "Cookie Cult", "Sugar Rush Hour", "Chocolate River", "Oven Overclock", "Moonlit Bakery",
    "Cookie Monsters", "Heavenly Chips", "Dough Dimension", "Sweet Singularity", "Cookie Universe")

// 70 baker upgrades + 15 click upgrades + 15 global upgrades = 100
private val upgrades: List<Upgrade> = buildList {
    producers.forEachIndexed { p, pr ->
        for (t in 0 until 10) add(Upgrade(size, "${pr.name}: ${tierNames[t]}", "Each ${pr.name.lowercase()} makes twice the cookies",
            pr.emoji, pr.baseCost * 10 * 6.0.pow(t), 0, p, tierReq[t].toDouble()))
    }
    for (i in 0 until 15) add(Upgrade(size, clickNames[i], "Every click is twice as strong", "\uD83D\uDCAA",
        100 * 5.0.pow(i), 1, 0, ceil(10 * 1.9.pow(i))))
    for (i in 0 until 15) {
        val cost = 5000 * 6.0.pow(i)
        add(Upgrade(size, globalNames[i], "+10% cookies per second", "\u2728", cost, 2, 0, cost / 2))
    }
}

private fun producerCost(p: Producer, owned: Int): Double = ceil(p.baseCost * 1.15.pow(owned))
private fun clickUpgradeCost(level: Int): Double = ceil(50 * 2.5.pow(level))

private fun fmt(v: Double): String {
    if (v < 1_000_000) return "%,d".format(Locale.US, v.toLong())
    val names = listOf("million", "billion", "trillion", "quadrillion", "quintillion", "sextillion", "septillion")
    var x = v / 1e6
    var i = 0
    while (x >= 1000 && i < names.lastIndex) { x /= 1000; i++ }
    return "%.2f %s".format(Locale.US, x, names[i])
}
private fun fmtRate(v: Double): String = if (v < 10) "%.1f".format(Locale.US, v) else fmt(v)

// ---------------------------------------------------------------- game state

private class Game(private val prefs: SharedPreferences) {
    var cookies by mutableDoubleStateOf(prefs.getString("cookies", null)?.toDoubleOrNull() ?: 0.0)
    var baked by mutableDoubleStateOf(prefs.getString("baked", null)?.toDoubleOrNull() ?: 0.0)
    var clicks by mutableLongStateOf(prefs.getLong("clicks", 0L))
    var clickLevel by mutableIntStateOf(prefs.getInt("clickLevel", 0))
    var clickMult by mutableDoubleStateOf(prefs.getString("clickMult", null)?.toDoubleOrNull() ?: 1.0)
    var cpsMult by mutableDoubleStateOf(prefs.getString("cpsMult", null)?.toDoubleOrNull() ?: 1.0)
    var freeBuy by mutableStateOf(prefs.getBoolean("freeBuy", false))
    val owned = mutableStateListOf<Int>().apply {
        val s = (prefs.getString("owned", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }
        producers.indices.forEach { add(s.getOrElse(it) { 0 }) }
    }
    val bought = mutableStateListOf<Boolean>().apply {
        val s = prefs.getString("bought", "") ?: ""
        upgrades.indices.forEach { add(s.getOrNull(it) == '1') }
    }

    private fun count(kind: Int, target: Int = -1) =
        upgrades.count { it.kind == kind && bought[it.id] && (target < 0 || it.target == target) }

    val clickPower: Double get() = (1 + clickLevel) * 2.0.pow(count(1)) * clickMult
    val cps: Double
        get() {
            val global = 1.1.pow(count(2)) * cpsMult
            return producers.indices.sumOf { producers[it].cps * owned[it] * 2.0.pow(count(0, it)) } * global
        }

    fun earn(x: Double) { cookies += x; baked += x }
    fun tap() { earn(clickPower); clicks++ }
    fun price(c: Double) = if (freeBuy) 0.0 else c
    fun cost(c: Double) = if (freeBuy) "FREE" else fmt(c)
    fun canAfford(c: Double) = cookies >= price(c)

    fun isUnlocked(u: Upgrade) = when (u.kind) {
        0 -> owned[u.target] >= u.req
        1 -> clicks >= u.req
        else -> baked >= u.req
    }
    fun requirement(u: Upgrade) = when (u.kind) {
        0 -> "Own ${u.req.toInt()} ${producers[u.target].name}s"
        1 -> "Click ${fmt(u.req)} times"
        else -> "Bake ${fmt(u.req)} cookies in total"
    }

    fun buyClick() { val c = price(clickUpgradeCost(clickLevel)); if (cookies >= c) { cookies -= c; clickLevel++; save() } }
    fun buyProducer(i: Int) { val c = price(producerCost(producers[i], owned[i])); if (cookies >= c) { cookies -= c; owned[i]++; save() } }
    fun buyUpgrade(u: Upgrade) { val c = price(u.cost); if (!bought[u.id] && cookies >= c) { cookies -= c; bought[u.id] = true; save() } }

    fun offlineGain(): Double {
        val last = prefs.getLong("lastSaved", 0L)
        if (last <= 0L) return 0.0
        return cps * ((System.currentTimeMillis() - last) / 1000L).coerceIn(0L, 8L * 3600L) * 0.5
    }

    fun reset() {
        cookies = 0.0; baked = 0.0; clicks = 0; clickLevel = 0; clickMult = 1.0; cpsMult = 1.0; freeBuy = false
        owned.indices.forEach { owned[it] = 0 }
        bought.indices.forEach { bought[it] = false }
        save()
    }

    fun save() {
        prefs.edit()
            .putString("cookies", cookies.toString()).putString("baked", baked.toString())
            .putLong("clicks", clicks).putInt("clickLevel", clickLevel)
            .putString("clickMult", clickMult.toString()).putString("cpsMult", cpsMult.toString())
            .putBoolean("freeBuy", freeBuy)
            .putString("owned", owned.joinToString(","))
            .putString("bought", bought.joinToString("") { if (it) "1" else "0" })
            .putLong("lastSaved", System.currentTimeMillis())
            .apply()
    }
}

// ---------------------------------------------------------------- main screen

private class Pop(val id: Long, val x: Float, val y: Float, val text: String)

@Composable
private fun GameScreen() {
    val context = LocalContext.current
    val game = remember { Game(context.getSharedPreferences("cookie_save", Context.MODE_PRIVATE)) }

    LaunchedEffect(Unit) {
        val gain = game.offlineGain()
        if (gain >= 1.0) {
            game.earn(gain)
            Toast.makeText(context, "Welcome back! Your bakers made ${fmt(gain)} cookies", Toast.LENGTH_LONG).show()
        }
        var last = System.nanoTime()
        var sinceSave = 0.0
        while (true) {
            delay(100)
            val now = System.nanoTime()
            val dt = (now - last) / 1e9
            last = now
            game.earn(game.cps * dt)
            sinceSave += dt
            if (sinceSave >= 1.0) { sinceSave = 0.0; game.save() }
        }
    }

    var tab by remember { mutableIntStateOf(0) }
    var showPin by remember { mutableStateOf(false) }
    var showAdmin by remember { mutableStateOf(false) }
    var titleTaps by remember { mutableIntStateOf(0) }
    var lastTitleTap by remember { mutableLongStateOf(0L) }
    val available = upgrades.count { !game.bought[it.id] && game.isUnlocked(it) }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF3D2417), Color(0xFF1B0F0A))))) {
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Cookie Clicker", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Caramel,
                modifier = Modifier.padding(top = 8.dp).clickable(remember { MutableInteractionSource() }, null) {
                    val now = System.currentTimeMillis()
                    titleTaps = if (now - lastTitleTap < 1500) titleTaps + 1 else 1
                    lastTitleTap = now
                    if (titleTaps >= 5) { titleTaps = 0; showPin = true }
                },
            )
            Text(fmt(game.cookies), fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
            Text("cookies", fontSize = 13.sp, color = Muted)
            Surface(shape = RoundedCornerShape(50), color = Color(0x33E0A15C), modifier = Modifier.padding(top = 6.dp)) {
                Text("\u26A1 ${fmtRate(game.cps)} per second", fontSize = 13.sp, color = Caramel,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp))
            }

            CookieButton(game, Modifier.weight(1f).fillMaxWidth())

            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = Caramel) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Bakers", fontWeight = FontWeight.Bold) })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text("Upgrades ($available)", fontWeight = FontWeight.Bold) })
            }

            LazyColumn(
                modifier = Modifier.weight(1.15f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(12.dp),
            ) {
                if (tab == 0) {
                    item {
                        val c = clickUpgradeCost(game.clickLevel)
                        ShopCard("\uD83D\uDC46", "Stronger Clicks", "+1 per click \u2022 level ${game.clickLevel}", game.canAfford(c)) {
                            BuyButton("$COOKIE ${game.cost(c)}", game.canAfford(c)) { game.buyClick() }
                        }
                    }
                    items(producers.indices.toList()) { i ->
                        val p = producers[i]
                        val c = producerCost(p, game.owned[i])
                        ShopCard(p.emoji, p.name, "${game.owned[i]} owned \u2022 +${fmtRate(p.cps)}/s each", game.canAfford(c)) {
                            BuyButton("$COOKIE ${game.cost(c)}", game.canAfford(c)) { game.buyProducer(i) }
                        }
                    }
                } else {
                    val sorted = upgrades.sortedWith(compareBy<Upgrade>({ game.bought[it.id] }, { !game.isUnlocked(it) }, { it.cost }))
                    items(sorted, key = { it.id }) { u ->
                        val owned = game.bought[u.id]
                        val unlocked = game.isUnlocked(u)
                        ShopCard(u.emoji, u.name, if (!owned && !unlocked) "\uD83D\uDD12 ${game.requirement(u)}" else u.desc,
                            highlight = !owned && unlocked && game.canAfford(u.cost), dim = owned || !unlocked) {
                            when {
                                owned -> Text("\u2713 Owned", color = Caramel, fontWeight = FontWeight.Bold)
                                unlocked -> BuyButton("$COOKIE ${game.cost(u.cost)}", game.canAfford(u.cost)) { game.buyUpgrade(u) }
                                else -> Text(fmt(u.cost), color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPin) PinDialog(onDismiss = { showPin = false }, onOk = { showPin = false; showAdmin = true })
    if (showAdmin) AdminPanel(game) { showAdmin = false }
}

// ---------------------------------------------------------------- cookie

@Composable
private fun CookieButton(game: Game, modifier: Modifier) {
    val pops = remember { mutableStateListOf<Pop>() }
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press",
    )
    val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(
        0.98f, 1.03f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(250.dp).pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { pos ->
                        game.tap()
                        pops.add(Pop(System.nanoTime(), pos.x - 30f, pos.y - 50f, "+${fmt(game.clickPower)}"))
                        if (pops.size > 14) pops.removeAt(0)
                    },
                )
            },
        ) {
            CookieArt(Modifier.fillMaxSize().scale(press * breathe))
            for (p in pops) key(p.id) { FloatingText(p) { pops.remove(p) } }
        }
    }
}

@Composable
private fun FloatingText(p: Pop, onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(800)); onDone() }
    Text(
        p.text, color = Cream, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.offset { IntOffset(p.x.toInt(), (p.y - t.value * 170f).toInt()) }.alpha(1f - t.value),
    )
}

@Composable
private fun CookieArt(modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(Brush.radialGradient(listOf(Color(0x77FFC46B), Color.Transparent), c, r), r, c)
        val body = r * 0.86f
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFF3C27A), Color(0xFFD28C40), Color(0xFFA9622A)),
                Offset(c.x - r * 0.25f, c.y - r * 0.3f), r * 1.2f,
            ), body, c,
        )
        drawCircle(Color(0x66FFE3B0), body, c, style = Stroke(r * 0.035f))
        val chips = listOf(
            Triple(-0.40f, -0.35f, 0.15f), Triple(0.12f, -0.52f, 0.13f), Triple(0.46f, -0.18f, 0.15f),
            Triple(-0.12f, -0.04f, 0.13f), Triple(0.22f, 0.26f, 0.15f), Triple(-0.52f, 0.18f, 0.14f),
            Triple(0.40f, 0.46f, 0.12f), Triple(-0.12f, 0.50f, 0.13f), Triple(-0.30f, -0.60f, 0.10f),
        )
        for ((dx, dy, s) in chips) {
            val w = s * r * 1.5f
            val h = s * r * 1.1f
            val tl = Offset(c.x + dx * r - w / 2, c.y + dy * r - h / 2)
            drawOval(Color(0xFF3A2013), tl, Size(w, h))
            drawOval(Color(0x55FFFFFF), Offset(tl.x + w * 0.2f, tl.y + h * 0.15f), Size(w * 0.35f, h * 0.25f))
        }
    }
}

// ---------------------------------------------------------------- shop widgets

@Composable
private fun ShopCard(
    emoji: String, title: String, subtitle: String, highlight: Boolean, dim: Boolean = false,
    action: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (dim) 0.6f else 1f),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBrown, contentColor = Cream),
        border = BorderStroke(1.dp, if (highlight) Caramel else Color(0x22FFFFFF)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF6B4630), Color(0xFF3A2216)))),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 24.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 12.sp, color = Muted)
            }
            Spacer(Modifier.width(8.dp))
            action()
        }
    }
}

@Composable
private fun BuyButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = Caramel, contentColor = Brown,
            disabledContainerColor = Color(0xFF5A4636), disabledContentColor = Color(0xFFB8A58E),
        ),
    ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
}

// ---------------------------------------------------------------- admin

@Composable
private fun PinDialog(onDismiss: () -> Unit, onOk: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Admin") },
        text = {
            OutlinedTextField(
                value = pin, onValueChange = { pin = it; wrong = false }, singleLine = true, isError = wrong,
                label = { Text(if (wrong) "Wrong PIN" else "PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
        },
        confirmButton = { TextButton(onClick = { if (pin == ADMIN_PIN) onOk() else wrong = true }) { Text("Enter") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BtnRow(vararg items: Pair<String, () -> Unit>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, action) ->
            FilledTonalButton(onClick = action, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) {
                Text(label, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AdminTitle(text: String) =
    Text(text, color = Caramel, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))

@Composable
private fun AdminPanel(game: Game, onClose: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }
    fun add(x: Double) { game.cookies += x; game.save() }
    fun warp(secs: Double) { game.earn(game.cps * secs); game.save() }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp), color = Color(0xFF241510),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83D\uDEE0 Admin panel", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("Close") }
                }
                Text(
                    "Cookies: ${fmt(game.cookies)}\nBaked all-time: ${fmt(game.baked)}\nPer second: ${fmtRate(game.cps)}\n" +
                        "Clicks: ${game.clicks} (power ${fmt(game.clickPower)})\nUpgrades: ${game.bought.count { it }}/${upgrades.size}",
                    color = Muted, fontSize = 13.sp,
                )

                AdminTitle("Add cookies")
                BtnRow("+1K" to { add(1e3) }, "+1M" to { add(1e6) }, "+1B" to { add(1e9) }, "+1T" to { add(1e12) })
                BtnRow("+1Qa" to { add(1e15) }, "+1Qi" to { add(1e18) }, "\u00D710" to { game.cookies *= 10; game.save() }, "Zero" to { game.cookies = 0.0; game.save() })
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Custom amount (e.g. 5e9)") },
                    trailingIcon = { TextButton(onClick = { amount.trim().toDoubleOrNull()?.let { add(it); amount = "" } }) { Text("Add") } },
                )

                AdminTitle("Click power multiplier (now \u00D7${fmt(game.clickMult)})")
                BtnRow("\u00D71" to { game.clickMult = 1.0; game.save() }, "\u00D710" to { game.clickMult = 10.0; game.save() },
                    "\u00D7100" to { game.clickMult = 100.0; game.save() }, "\u00D71000" to { game.clickMult = 1000.0; game.save() })

                AdminTitle("Cookies/sec multiplier (now \u00D7${fmt(game.cpsMult)})")
                BtnRow("\u00D71" to { game.cpsMult = 1.0; game.save() }, "\u00D710" to { game.cpsMult = 10.0; game.save() },
                    "\u00D7100" to { game.cpsMult = 100.0; game.save() }, "\u00D71000" to { game.cpsMult = 1000.0; game.save() })

                AdminTitle("Time warp (earn production instantly)")
                BtnRow("+1h" to { warp(3600.0) }, "+8h" to { warp(8 * 3600.0) }, "+24h" to { warp(86400.0) }, "+7d" to { warp(7 * 86400.0) })

                AdminTitle("Bakers")
                BtnRow("+1 each" to { game.owned.indices.forEach { game.owned[it] += 1 }; game.save() },
                    "+10 each" to { game.owned.indices.forEach { game.owned[it] += 10 }; game.save() },
                    "+100 each" to { game.owned.indices.forEach { game.owned[it] += 100 }; game.save() },
                    "Clear" to { game.owned.indices.forEach { game.owned[it] = 0 }; game.save() })

                AdminTitle("Upgrades")
                BtnRow("Unlock all" to { game.bought.indices.forEach { game.bought[it] = true }; game.save() },
                    "Remove all" to { game.bought.indices.forEach { game.bought[it] = false }; game.save() })

                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Free purchases", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Switch(checked = game.freeBuy, onCheckedChange = { game.freeBuy = it; game.save() })
                }

                Button(
                    onClick = { if (confirmReset) { game.reset(); confirmReset = false } else confirmReset = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E2B2B), contentColor = Cream),
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                ) { Text(if (confirmReset) "Tap again to erase ALL progress" else "Reset everything") }
            }
        }
    }
}
