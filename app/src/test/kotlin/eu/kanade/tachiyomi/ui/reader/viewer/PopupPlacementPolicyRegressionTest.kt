package eu.kanade.tachiyomi.ui.reader.viewer

import org.junit.jupiter.api.Test
import kotlin.random.Random

class PopupPlacementPolicyRegressionTest {
    @Test
    fun sourceBoundsAndSmallViewports() = runYomitanPlacementRegressionChecks()
}

internal fun runYomitanPlacementRegressionChecks() {
    fun overlaps(p: PopupPlacement, r: PopupSourceRect): Boolean =
        p.width > 0.01f && p.height > 0.01f && p.x < r.right - 0.02f &&
            p.x + p.width > r.left + 0.02f && p.y < r.bottom - 0.02f && p.y + p.height > r.top + 0.02f
    fun validate(p: PopupPlacement, sources: List<PopupSourceRect>, w: Float, h: Float) {
        check(listOf(p.x,p.y,p.width,p.height).all { it.isFinite() })
        check(p.x >= -0.01f && p.y >= -0.01f && p.width >= 0f && p.height >= 0f)
        check(p.x+p.width <= w+0.05f && p.y+p.height <= h+0.05f)
        check(sources.none { overlaps(p,it) }) { "Text overlap: $p sources=$sources" }
    }
    // Same approximate proportions as the reported 准备 screen capture.
    val selected=PopupSourceRect(252f,840f,388f,897f)
    val p=PopupPlacementPolicy.bestHorizontal(listOf(selected),638f,900f,709f,1536f,8f,16f,true)
    validate(p,listOf(selected),709f,1536f)
    check(!p.after && p.y+p.height <= 824.01f && p.height < 900f)
    // Multi-line selection: do not place a popup across the second matched line.
    val lines=listOf(PopupSourceRect(30f,400f,200f,450f),PopupSourceRect(20f,455f,160f,510f))
    validate(PopupPlacementPolicy.bestHorizontal(lines,350f,480f,412f,850f,8f,12f,true),lines,412f,850f)
    // No safe area: a zero dimension is safer than a 1px overlap/out-of-bounds frame.
    val whole=PopupSourceRect(0f,0f,412f,850f)
    val none=PopupPlacementPolicy.bestHorizontal(listOf(whole),350f,500f,412f,850f,8f,12f,true)
    check(none.height == 0f)
    validate(none,listOf(whole),412f,850f)
    // Requested preference is honored when both sides fit (Yomitan behavior).
    val mid=PopupSourceRect(100f,400f,150f,450f)
    check(PopupPlacementPolicy.horizontal(mid,100f,100f,412f,850f,8f,12f,true).after)
    check(!PopupPlacementPolicy.horizontal(mid,100f,100f,412f,850f,8f,12f,false).after)
    val random=Random(20261003)
    repeat(20000) { i ->
        val w=random.nextInt(1,2200).toFloat(); val h=random.nextInt(1,3000).toFloat()
        val x=random.nextFloat()*w; val y=random.nextFloat()*h
        val sources=listOf(PopupSourceRect(x,y,x+random.nextFloat()*(w-x),y+random.nextFloat()*(h-y)))
        val pad=random.nextFloat()*minOf(w,h)/2f
        val gap=random.nextFloat()*30f
        val result=if (i%2==0) PopupPlacementPolicy.bestHorizontal(sources,w*random.nextFloat()*2f,h*random.nextFloat()*2f,w,h,pad,gap,true)
          else PopupPlacementPolicy.bestVertical(sources,w*random.nextFloat()*2f,h*random.nextFloat()*2f,w,h,pad,gap,false)
        validate(result,sources,w,h)
    }
}
