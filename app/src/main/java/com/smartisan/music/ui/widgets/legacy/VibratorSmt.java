package com.smartisan.music.ui.widgets.legacy;

import android.os.Vibrator;

public final class VibratorSmt {
    private VibratorSmt() {
    }

    @SuppressWarnings("deprecation")
    public static void vibrateEffect(Vibrator vibrator, int effect) {
        if (vibrator != null) {
            vibrator.vibrate(30L);
        }
    }
}
