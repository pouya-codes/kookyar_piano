package com.PouyaApp.kookyarpiano;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceFragmentCompat;

import com.kookyar.common.EdgeToEdge;

/** Settings screen: reference pitch and microphone sensitivity. */
public class Prefs extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_prefs);

		Toolbar toolbar = findViewById(R.id.app_bar);
		setSupportActionBar(toolbar);
		if (getSupportActionBar() != null) {
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			getSupportActionBar().setTitle(R.string.tanzimat);
		}
		EdgeToEdge.apply(this, findViewById(R.id.prefs_root), toolbar, findViewById(R.id.prefs_container));

		if (savedInstanceState == null) {
			getSupportFragmentManager()
					.beginTransaction()
					.replace(R.id.prefs_container, new PrefsFragment())
					.commit();
		}
	}

	@Override
	public boolean onSupportNavigateUp() {
		finish();
		return true;
	}

	public static class PrefsFragment extends PreferenceFragmentCompat {
		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			setPreferencesFromResource(R.xml.perfs, rootKey);
		}
	}
}
