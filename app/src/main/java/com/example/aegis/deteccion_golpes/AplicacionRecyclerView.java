package com.example.aegis.deteccion_golpes;

import android.app.Application;

import java.util.ArrayList;
import java.util.List;

public class AplicacionRecyclerView extends Application {
    public List<Golpes> golpes = new ArrayList<>();
    public AdaptadorGolpes adaptadorGolpes = new AdaptadorGolpes(golpes);

    @Override public void onCreate() {
        super.onCreate();
    }
}
