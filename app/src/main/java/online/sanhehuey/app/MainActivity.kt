package online.sanhehuey.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {

    private var model: AppModel? = null

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            model?.onVpnPermission(result.resultCode == RESULT_OK)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "2.0"

        val m = AppModel(this, lifecycleScope)
        m.requestVpnPermission = { intent -> vpnPermission.launch(intent) }
        model = m

        setContent { AppRoot(m, version) }
        m.start()
    }

    override fun onResume() {
        super.onResume()
        model?.refresh()
    }
}
