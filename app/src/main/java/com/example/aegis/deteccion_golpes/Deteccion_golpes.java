package com.example.aegis.deteccion_golpes;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aegis.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class Deteccion_golpes extends AppCompatActivity {
    private RecyclerView recyclerView;
    public AdaptadorGolpes adaptadorGolpes;
    private List<Golpes> golpes;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.golpes_recibidos_historial);

        // Obtener la lista y el adaptador de AplicacionRecyclerView
        AplicacionRecyclerView app = (AplicacionRecyclerView) getApplication();
        golpes = app.golpes;
        adaptadorGolpes = app.adaptadorGolpes;

        // Configurar RecyclerView
        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adaptadorGolpes);

        // Inicializar Firebase
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Cargar golpes indicando el ID de la maleta (ej: "Maleta 2")
        cargarGolpesFirebase("Maleta 2");


    }

    private void cargarGolpesFirebase(String idMaleta) {
        FirebaseUser usuarioActual = auth.getCurrentUser();
        if (usuarioActual == null) return;

        String uid = usuarioActual.getUid();

        // Ruta: Usuarios -> {UUID} -> Maletas -> {idMaleta} -> Sensores -> Sensor tilt -> Historial_golpes
        db.collection("Usuarios")
                .document(uid)
                .collection("Maletas")
                .document(idMaleta)
                .collection("Sensores")
                .document("Sensor tilt")
                .collection("Historial_golpes")
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    if (error != null) {
                        Log.e("Deteccion_golpes", "Error al consultar la BBDD", error);
                        return;
                    }

                    if (queryDocumentSnapshots != null) {
                        golpes.clear();
                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            Golpes golpe = doc.toObject(Golpes.class);
                            if (golpe != null) {
                                golpe.setId(doc.getId());
                                golpes.add(golpe);
                            }
                        }

                        adaptadorGolpes.notifyDataSetChanged();
                    }
                });
    }

    public void onClickVolver(View view) {
        finish();
    }
}
