package com.github.lightjunction.magicbox

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CloudRefineTest {
    private fun web(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) web(view.getChildAt(index))?.let { return it }
        return null
    }

    private fun js(scenario: ActivityScenario<CloudActivity>, code: String): String {
        val done = CountDownLatch(1)
        var value = ""
        scenario.onActivity { activity ->
            web(activity.findViewById(android.R.id.content))!!.evaluateJavascript(code) {
                value = it
                done.countDown()
            }
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        return value
    }

    private fun ready(scenario: ActivityScenario<CloudActivity>) {
        for (attempt in 0 until 40) {
            if (js(scenario, "typeof MagicClick === 'object' && typeof CloudUI === 'object'") == "true") return
            Thread.sleep(250)
        }
        fail("Refined UI did not load")
    }

    @Test fun navigationClickStillProducesVisibleCharacterBurst() {
        ActivityScenario.launch(CloudActivity::class.java).use { scenario ->
            ready(scenario)
            js(scenario, "if(document.getElementById('reduce-motion').getAttribute('aria-checked')==='true')document.getElementById('reduce-motion').click();document.querySelector('[data-page=home]').click()")
            Thread.sleep(900)
            val before = js(scenario, "MagicClick.metrics().frames").toInt()
            js(scenario, "document.getElementById('choose-node').click()")
            Thread.sleep(140)
            assertEquals("false", js(scenario, "document.getElementById('page-subscriptions').hidden"))
            assertTrue(js(scenario, "MagicClick.metrics().active").toInt() > 0)
            assertTrue(js(scenario, "MagicClick.metrics().frames").toInt() > before)
            Thread.sleep(950)
            assertEquals("0", js(scenario, "MagicClick.metrics().active"))
        }
    }

    @Test fun visibleInterfaceDoesNotNameOrExplainTheVisualLanguage() {
        ActivityScenario.launch(CloudActivity::class.java).use { scenario ->
            ready(scenario)
            val body = js(scenario, "document.body.innerText")
            for (copy in listOf("token 云集合", "令牌云集合", "一切从一个连接开始", "导入后，节点会出现在这里", "聚合成形", "没有液态玻璃")) {
                assertFalse("Visible copy still contains: $copy", body.contains(copy))
            }
            assertEquals("MagicBox", js(scenario, "document.title").trim('"'))
        }
    }
}
