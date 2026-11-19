package com.example.aegis.deteccion_golpes;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.aegis.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class Maps extends AppCompatActivity implements OnMapReadyCallback {

    private double latitud = 0.0;
    private double longitud = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.golpes_recibidos_mas_informacion);

        // Al pulsar fuera o en la X, solo se cierra este popup (Maps), sin cerrar Deteccion_golpes
        setFinishOnTouchOutside(true);

        ImageView btnCerrar = findViewById(R.id.boton_cerrar_btnINF);
        TextView tvFecha = findViewById(R.id.fecha);
        TextView tvHora = findViewById(R.id.hora);

        // Cerrar únicamente este popup al pulsar la X
        btnCerrar.setOnClickListener(v -> finish());

        // Recibir los valores del Intent
        if (getIntent() != null) {
            String fecha = getIntent().getStringExtra("fecha");
            String hora = getIntent().getStringExtra("hora");
            latitud = getIntent().getDoubleExtra("latitud", 0.0);
            longitud = getIntent().getDoubleExtra("longitud", 0.0);

            if (fecha != null) tvFecha.setText("Fecha: " + fecha);
            if (hora != null) tvHora.setText("Hora: " + hora);
        }

        // Cargar el mapa
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        // Posicionar en latitud y longitud recibidas
        LatLng posicionImpacto = new LatLng(latitud, longitud);
        googleMap.addMarker(new MarkerOptions()
                .position(posicionImpacto)
                .title("Ubicación del golpe"));

        // Nivel de zoom 15
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(posicionImpacto, 15f));
    }
}