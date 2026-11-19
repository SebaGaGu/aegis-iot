package com.example.aegis.deteccion_golpes;

import android.app.Application;

import java.util.List;

public class AplicacionRecyclerView extends Application {
    private List<Golpes> golpes;
    public AdaptadorGolpes adaptadorGolpes = new AdaptadorGolpes(golpes);

    @Override public void onCreate() {
        super.onCreate();
    }
}
