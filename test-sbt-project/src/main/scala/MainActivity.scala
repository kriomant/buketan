package net.kriomant.buketan_sbt_test

import _root_.android.app.Activity
import _root_.android.os.Bundle

class MainActivity extends Activity with TypedActivity {
  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)
    setContentView(R.layout.main)

    findView(TR.textview).setText("hello, world!")

	  val chatRes = R.drawable.chat
	  val testRes = R.drawable.test
  }
}
