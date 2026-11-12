package com.PouyaApp.kookyarpiano;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.kookyar.common.Anim;
import com.kookyar.common.EdgeToEdge;
import com.kookyar.common.FeatureBar;
import com.kookyar.common.MetronomeLauncher;
import com.kookyar.common.PitchView;
import com.kookyar.common.RadifLauncher;
import com.kookyar.common.TextPageActivity;
import com.kookyar.common.TunerPitchCoordinator;

import org.puredata.android.io.AudioParameters;
import org.puredata.android.service.PdService;
import org.puredata.android.utils.PdUiDispatcher;
import org.puredata.core.PdBase;
import org.puredata.core.PdListener;
import org.puredata.core.utils.IoUtils;

import java.io.File;
import java.io.IOException;

/**
 * KookYar Piano: tap any of the 88 keys (or let the tuner find the key you
 * are playing) and tune it against the reference tone and the gauge.
 */
public class PianoTuner extends AppCompatActivity {

    private static final String TAG = "KookYaR";
    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 1001;
    private static final String PREF_LAST_KEY = "piano_last_key";
    private static final int DEFAULT_KEY = 69; // A4

    private static final int HIGH_SENSITIVE = 55;
    private static final int MID_SENSITIVE = 50;
    private static final int LOW_SENSITIVE = 45;
    /** Octave jump buttons: A0 then every C. */
    private static final int[] OCTAVE_JUMPS = {21, 36, 48, 60, 72, 84, 96, 108};

    private PdService pdService;
    private boolean pdBound;
    private PdUiDispatcher dispatcher;
    private final TunerPitchCoordinator pitchCoordinator = new TunerPitchCoordinator();

    private PianoKeyboardView keyboard;
    private PitchView pitchView;
    private TextView pitchLabel;
    private TextView keyHzLabel;
    private MaterialSwitch autoSwitch;
    private MaterialButton[] octaveButtons;

    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;
    private int laFrequency = 440;
    private int sensitiveLevel = MID_SENSITIVE;
    private int pressure;
    private int selectedMidi = -1;
    private int lastAutoMidi = -1;
    private String[] noteNames;
    private String[] octaveNames;
    private String octaveWord;

    private final ServiceConnection pdConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            pdService = ((PdService.PdBinder) service).getService();
            try {
                initPd();
                loadPatch();
            } catch (IOException e) {
                Log.e(TAG, e.toString());
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            pdService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_piano_tuner);
        EdgeToEdge.apply(this, findViewById(R.id.tuner_root), findViewById(R.id.app_bar), findViewById(R.id.feature_bar));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        noteNames = getResources().getStringArray(com.kookyar.common.R.array.kookyar_note_names);
        octaveNames = getResources().getStringArray(com.kookyar.common.R.array.kookyar_octave_names);
        octaveWord = getString(com.kookyar.common.R.string.kookyar_gauge_octave);

        initGui();
        initPrefs();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_RECORD_AUDIO);
        } else {
            initAudio();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initAudio();
            } else {
                Toast.makeText(this, com.kookyar.common.R.string.kookyar_mic_required, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initAudio() {
        pdBound = bindService(new Intent(this, PdService.class), pdConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (pdService != null && !pdService.isRunning()) {
            pdService.startAudio();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (pdService != null && pdService.isRunning()) {
            pdService.stopAudio();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        pitchView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        pitchView.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pitchCoordinator.destroy();
        if (prefs != null && prefsListener != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
        }
        if (pdBound) {
            unbindService(pdConnection);
            pdBound = false;
        }
    }

    // ------------------------------------------------------------------ UI --

    private void initGui() {
        Toolbar toolbar = findViewById(R.id.app_bar);
        setSupportActionBar(toolbar);

        keyboard = findViewById(R.id.keyboard);
        pitchView = findViewById(R.id.pitch_view);
        pitchLabel = findViewById(R.id.pitch_label);
        keyHzLabel = findViewById(R.id.key_hz_label);
        autoSwitch = findViewById(R.id.checkBox_tuning);

        keyboard.setOnKeyListener(this::selectKey);
        pitchView.setStatusStrings(
                getString(R.string.key_select),
                getString(com.kookyar.common.R.string.kookyar_gauge_tighten_string),
                getString(com.kookyar.common.R.string.kookyar_gauge_loosen_string),
                getString(R.string.play_key));

        findViewById(R.id.tuning).setOnClickListener(v -> autoSwitch.setChecked(!autoSwitch.isChecked()));
        autoSwitch.setOnCheckedChangeListener((button, checked) -> {
            lastAutoMidi = -1;
            keyboard.setDetectedMidi(-1);
            if (checked) {
                Toast.makeText(this, R.string.auto_detect_hint, Toast.LENGTH_LONG).show();
            }
        });

        int[] ids = {R.id.octave_0, R.id.octave_1, R.id.octave_2, R.id.octave_3,
                R.id.octave_4, R.id.octave_5, R.id.octave_6, R.id.octave_7};
        octaveButtons = new MaterialButton[ids.length];
        for (int i = 0; i < ids.length; i++) {
            final int midi = OCTAVE_JUMPS[i];
            octaveButtons[i] = findViewById(ids[i]);
            octaveButtons[i].setText(PianoKeyboardView.scientificName(midi));
            octaveButtons[i].setOnClickListener(v -> keyboard.scrollToMidi(midi, true));
        }

        initFeatureBar();
    }

    /** Flat switcher: every feature is one tap from the tuner. */
    private void initFeatureBar() {
        FeatureBar featureBar = findViewById(R.id.feature_bar);
        featureBar.setAccentColor(ContextCompat.getColor(this, R.color.accentColor));
        featureBar.setInactiveColor(ContextCompat.getColor(this, R.color.onSurfaceVariant));
        featureBar.setItems(
                FeatureBar.Item.current(
                        getString(com.kookyar.common.R.string.kookyar_feature_tuner),
                        com.kookyar.common.R.drawable.ic_kookyar_tuner),
                FeatureBar.Item.of(
                        getString(com.kookyar.common.R.string.kookyar_feature_metronome),
                        com.kookyar.common.R.drawable.ic_kookyar_metronome,
                        () -> MetronomeLauncher.open(this)),
                FeatureBar.Item.of(
                        getString(com.kookyar.common.R.string.kookyar_feature_radif),
                        com.kookyar.common.R.drawable.ic_kookyar_radif,
                        () -> RadifLauncher.open(this)),
                FeatureBar.Item.of(
                        getString(com.kookyar.common.R.string.kookyar_feature_help),
                        com.kookyar.common.R.drawable.ic_kookyar_help,
                        () -> TextPageActivity.open(this, R.string.rahnama, R.string.TunerHelp)),
                FeatureBar.Item.of(
                        getString(com.kookyar.common.R.string.kookyar_feature_settings),
                        com.kookyar.common.R.drawable.ic_kookyar_settings,
                        () -> startActivity(new Intent(this, Prefs.class))));
    }

    private void initPrefs() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        applyPrefs();
        prefsListener = (p, key) -> {
            applyPrefs();
            if (selectedMidi > 0) {
                // The reference changed: re-aim the gauge at the same key.
                showTarget(selectedMidi, false);
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);

        int last = prefs.getInt(PREF_LAST_KEY, DEFAULT_KEY);
        keyboard.post(() -> {
            keyboard.scrollToMidi(last, false);
            selectKey(last);
        });
    }

    private void applyPrefs() {
        laFrequency = Integer.parseInt(prefs.getString("ref", "440"));
        pitchView.setAFrequnse(laFrequency);
        String s = prefs.getString("sensitive", "2");
        if ("3".equals(s)) {
            sensitiveLevel = HIGH_SENSITIVE;
        } else if ("1".equals(s)) {
            sensitiveLevel = MID_SENSITIVE;
        } else {
            sensitiveLevel = LOW_SENSITIVE;
        }
    }

    /** The user tapped a key: play its reference tone and aim the gauge at it. */
    private void selectKey(int midi) {
        selectedMidi = midi;
        lastAutoMidi = -1;
        keyboard.setSelectedMidi(midi);
        keyboard.setDetectedMidi(-1);
        prefs.edit().putInt(PREF_LAST_KEY, midi).apply();
        showTarget(midi, true);
        Anim.pulse(pitchLabel);
    }

    /** Auto-detect found the player on another key: follow it without playing a tone. */
    private void autoSelect(int midi) {
        if (midi == lastAutoMidi) {
            return;
        }
        lastAutoMidi = midi;
        selectedMidi = midi;
        keyboard.setSelectedMidi(midi);
        keyboard.setDetectedMidi(midi);
        keyboard.scrollToMidi(midi, true);
        showTarget(midi, false);
    }

    private void showTarget(int midi, boolean playTone) {
        float realMidi = midiToRealMidi(midi);
        pitchCoordinator.invalidatePitchReset();
        pitchView.setCenterPitch(realMidi);
        pitchView.setCurrentPitch(PitchView.NO_PITCH);
        pitchView.setMidiRef(midi);
        pitchLabel.setText(keyLabel(midi));
        keyHzLabel.setText(String.format(java.util.Locale.US, "%s  ·  %.2f Hz",
                PianoKeyboardView.scientificName(midi), midiToHz(midi)));
        if (playTone) {
            final float midiForPd = realMidi;
            pitchCoordinator.scheduleTrigger(() -> TunerPitchCoordinator.runPdSend(() -> {
                PdBase.sendFloat("midinote", midiForPd);
                PdBase.sendBang("trigger");
            }));
        }
    }

    /** Persian/English note name with octave, e.g. "دو اکتاو چهارم". */
    private String keyLabel(int midi) {
        int pc = midi % 12;
        int octaveIndex = midi / 12 - 1; // scientific octave; names start at octave 0
        String octave = octaveIndex >= 0 && octaveIndex < octaveNames.length
                ? octaveNames[octaveIndex] : String.valueOf(octaveIndex);
        return noteNames[pc] + " " + octaveWord + " " + octave;
    }

    // --------------------------------------------------------------- audio --

    private void initPd() throws IOException {
        AudioParameters.init(this);
        int sampleRate = AudioParameters.suggestSampleRate();
        pdService.initAudio(sampleRate, 1, 2, 20.0f);
        if (!pdService.isRunning()) {
            pdService.startAudio();
        }
        pitchCoordinator.setAudioStarter(() -> {
            if (pdService != null && !pdService.isRunning()) {
                pdService.startAudio();
            }
        });

        dispatcher = new PdUiDispatcher();
        PdBase.setReceiver(dispatcher);

        dispatcher.addListener("pitch", new PdListener.Adapter() {
            @Override
            public void receiveFloat(String source, final float x) {
                if (pressure < sensitiveLevel || pitchView.getCenterPitch() <= 0) {
                    return;
                }
                final float pitch = Math.round(x * 1000) / 1000f;
                pitchCoordinator.onPitchInput(
                        () -> {
                            pitchView.setCurrentPitch(pitch);
                            if (autoSwitch.isChecked()) {
                                int nearest = Math.round(pitch);
                                float target = midiToRealMidi(nearest);
                                if (nearest >= PianoKeyboardView.FIRST_MIDI
                                        && nearest <= PianoKeyboardView.LAST_MIDI
                                        && Math.abs(pitch - target) <= 0.4f) {
                                    autoSelect(nearest);
                                }
                            }
                        },
                        () -> {
                            pitchView.setCurrentPitch(PitchView.NO_PITCH);
                            keyboard.setDetectedMidi(-1);
                        });
            }
        });

        dispatcher.addListener("pressure", new PdListener.Adapter() {
            @Override
            public void receiveFloat(String source, final float z) {
                pressure = (int) z;
            }
        });
    }

    private void loadPatch() throws IOException {
        File dir = getFilesDir();
        IoUtils.extractZipResource(getResources().openRawResource(R.raw.temp), dir, true);
        PdBase.openPatch(new File(dir, "path").getAbsolutePath());
    }

    /** Shifts a MIDI note so that A4 lands on the user's reference frequency. */
    private float midiToRealMidi(float midi) {
        double hz = laFrequency * Math.pow(2, (midi - 69) / 12.0);
        float real = (float) (69 + 12 * Math.log(hz / 440.0) / Math.log(2.0));
        return Math.round(real * 1000) / 1000f;
    }

    private double midiToHz(int midi) {
        return laFrequency * Math.pow(2, (midi - 69) / 12.0);
    }
}
