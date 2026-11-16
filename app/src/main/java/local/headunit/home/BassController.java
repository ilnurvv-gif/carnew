package local.headunit.home;

import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;

/** Глобальный bass boost (сессия 0 = весь выход). Объекты хранятся статически, пока жив процесс. */
public final class BassController {
    private static BassBoost boost;
    private static Equalizer eq;
    private static short[] savedLevels;

    private BassController() {}

    /** @return true если эффект включён/выключен без ошибок */
    public static synchronized boolean set(boolean on) {
        try {
            if (on) {
                release();
                boost = new BassBoost(1000, 0);
                if (boost.getStrengthSupported()) boost.setStrength((short) 1000);
                boost.setEnabled(true);
                try {
                    eq = new Equalizer(1000, 0);
                    short[] r = eq.getBandLevelRange();
                    int n = eq.getNumberOfBands();
                    savedLevels = new short[n];
                    for (short b = 0; b < n; b++) {
                        savedLevels[b] = eq.getBandLevel(b);
                        int hz = eq.getCenterFreq(b) / 1000; // мГц -> Гц
                        short lvl = savedLevels[b];
                        if (hz < 150) lvl = r[1];
                        else if (hz < 400) lvl = (short) (r[1] * 2 / 3);
                        else if (hz < 1000) lvl = (short) (r[1] / 4);
                        eq.setBandLevel(b, lvl);
                    }
                    eq.setEnabled(true);
                } catch (Throwable ignore) {
                    eq = null; // хватит и одного BassBoost
                }
            } else {
                release();
            }
            return true;
        } catch (Throwable t) {
            release();
            return false;
        }
    }

    private static void release() {
        try { if (boost != null) { boost.setEnabled(false); boost.release(); } } catch (Throwable ignore) {}
        try { if (eq != null) { eq.setEnabled(false); eq.release(); } } catch (Throwable ignore) {}
        boost = null; eq = null;
    }
}
