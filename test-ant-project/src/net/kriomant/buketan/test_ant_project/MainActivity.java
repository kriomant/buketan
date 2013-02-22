package net.kriomant.buketan.test_ant_project;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity
{
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

		int chatResource = R.drawable.chat;
		int testResource = R.drawable.test;
    }
}
