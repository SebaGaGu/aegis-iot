package com.example.aegis.deteccion_golpes;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aegis.R;

public class Deteccion_golpes extends AppCompatActivity {
    private RecyclerView recyclerView;
    public AdaptadorGolpes adaptadorGolpes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.golpes_recibidos_historial);

        adaptadorGolpes = ((AplicacionRecyclerView) getApplication()).adaptadorGolpes;
        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adaptadorGolpes);
}}
