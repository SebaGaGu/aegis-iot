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

        cargarGolpesFirebase();
    }

    private void cargarGolpesFirebase() {
        FirebaseUser usuarioActual = auth.getCurrentUser();
        if (usuarioActual == null) {
            Log.e("Deteccion_golpes", "No hay usuario autenticado.");
            return;
        }

        String uid = usuarioActual.getUid();
        Log.d("Deteccion_golpes", "UID del usuario actual: " + uid);

        // Ruta permitida por reglas de seguridad: Usuarios / {UID} / Maletas
        db.collection("Usuarios")
                .document(uid)
                .collection("Maletas")
                .addSnapshotListener((maletasSnapshots, errorMaletas) -> {
                    if (errorMaletas != null) {
                        Log.e("Deteccion_golpes", "Error al consultar maletas: ", errorMaletas);
                        // Si falla por ruta específica, intentamos por collectionGroup
                        cargarPorCollectionGroup();
                        return;
                    }

                    if (maletasSnapshots != null && !maletasSnapshots.isEmpty()) {
                        Log.d("Deteccion_golpes", "Maletas encontradas: " + maletasSnapshots.size());
                        for (DocumentSnapshot maletaDoc : maletasSnapshots.getDocuments()) {
                            String idMaleta = maletaDoc.getId();

                            // Escuchamos el Historial_golpes de Sensor_Tilt para cada maleta
                            db.collection("Usuarios")
                                    .document(uid)
                                    .collection("Maletas")
                                    .document(idMaleta)
                                    .collection("Sensores")
                                    .document("Sensor_Tilt")
                                    .collection("Historial_golpes")
                                    .addSnapshotListener((golpesSnapshots, errorGolpes) -> {
                                        if (errorGolpes != null) {
                                            Log.e("Deteccion_golpes", "Error al consultar golpes: ", errorGolpes);
                                            return;
                                        }

                                        if (golpesSnapshots != null) {
                                            Log.d("Deteccion_golpes", "Golpes recibidos: " + golpesSnapshots.size());
                                            golpes.clear();
                                            for (DocumentSnapshot doc : golpesSnapshots.getDocuments()) {
                                                try {
                                                    Golpes golpe = doc.toObject(Golpes.class);
                                                    if (golpe != null) {
                                                        golpe.setId(doc.getId());
                                                        golpes.add(golpe);
                                                    }
                                                } catch (Exception e) {
                                                    Log.e("Deteccion_golpes", "Error procesando golpe " + doc.getId(), e);
                                                }
                                            }
                                            adaptadorGolpes.notifyDataSetChanged();
                                        }
                                    });
                        }
                    } else {
                        Log.d("Deteccion_golpes", "No se encontraron maletas directamente, usando respaldo...");
                        cargarPorCollectionGroup();
                    }
                });
    }

    private void cargarPorCollectionGroup() {
        db.collectionGroup("Historial_golpes")
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    if (error != null) {
                        Log.e("Deteccion_golpes", "Error en collectionGroup: ", error);
                        return;
                    }

                    if (queryDocumentSnapshots != null) {
                        golpes.clear();
                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {







                            try {
                                Golpes golpe = doc.toObject(Golpes.class);
                                if (golpe != null) {
                                    golpe.setId(doc.getId());
                                    golpes.add(golpe);
                                }
                            } catch (Exception e) {
                                Log.e("Deteccion_golpes", "Error procesando golpe " + doc.getId(), e);
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
