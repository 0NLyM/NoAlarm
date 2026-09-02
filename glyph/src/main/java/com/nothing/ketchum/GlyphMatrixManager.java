package com.nothing.ketchum;

import android.content.ComponentName;
import android.content.Context;

public class GlyphMatrixManager {

    public interface Callback {
        void onServiceConnected(ComponentName componentName);
        void onServiceDisconnected(ComponentName componentName);
    }

    public static GlyphMatrixManager getInstance(Context context) { throw new RuntimeException("stub"); }

    public boolean init(Callback callback) { throw new RuntimeException("stub"); }
    public void unInit() { throw new RuntimeException("stub"); }
    public boolean register(String device) { throw new RuntimeException("stub"); }
    public void setMatrixFrame(int[] frame) { throw new RuntimeException("stub"); }
    public void setMatrixFrame(byte[] frame) { throw new RuntimeException("stub"); }
    public void setAppMatrixFrame(int[] frame) { throw new RuntimeException("stub"); }
    public void turnOff() { throw new RuntimeException("stub"); }
}
