package com.example.aegis;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class CuentaActivity extends AppCompatActivity {

    // Elementos de mi pantalla
    private TextView tvNombreUsuario;
    private TextView tvCorreoUsuario;
    private TextView tvEstadoConexion;

    private Button btnConectarMaleta;
    private Button btnCerrarSesion;

    // Firebase Authentication
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cuenta);

        // Busco los elementos que he creado en el XML
        tvNombreUsuario = findViewById(R.id.tvNombreUsuario);
        tvCorreoUsuario = findViewById(R.id.tvCorreoUsuario);
        tvEstadoConexion = findViewById(R.id.tvEstadoConexion);

        btnConectarMaleta = findViewById(R.id.btnConectarMaleta);
        btnCerrarSesion = findViewById(R.id.btnCerrarSesion);

        // Inicializo Firebase
        auth = FirebaseAuth.getInstance();

        // Muestro los datos del usuario que ha iniciado sesion
        mostrarDatosUsuario();

        // Cuando pulso conectar maleta
        btnConectarMaleta.setOnClickListener(v -> conectarMaleta());

        // Cuando pulso cerrar sesion
        btnCerrarSesion.setOnClickListener(v -> cerrarSesion());
    }


    // =========================================
    // MOSTRAR LOS DATOS DEL USUARIO
    // =========================================

    private void mostrarDatosUsuario() {

        // Obtengo el usuario que esta identificado en Firebase
        FirebaseUser usuario = auth.getCurrentUser();

        // Si no hay ningun usuario, vuelvo al login
        if (usuario == null) {

            Toast.makeText(
                    this,
                    "Debes iniciar sesión",
                    Toast.LENGTH_SHORT
            ).show();

            volverAlLogin();

            return;
        }

        // Obtengo el nombre y el correo del usuario
        String nombre = usuario.getDisplayName();
        String correo = usuario.getEmail();

        // Si el usuario no tiene nombre, muestro uno por defecto
        if (nombre == null || nombre.trim().isEmpty()) {
            nombre = "Usuario de AEGIS";
        }

        // Si el usuario no tiene correo, muestro un mensaje
        if (correo == null || correo.trim().isEmpty()) {
            correo = "Correo no disponible";
        }

        // Muestro los datos en mi pantalla
        tvNombreUsuario.setText(nombre);
        tvCorreoUsuario.setText(correo);

        // De momento la maleta no esta conectada
        tvEstadoConexion.setText("Sin conectar");
    }


    // =========================================
    // CONECTAR LA MALETA
    // =========================================

    private void conectarMaleta() {

        // La conexion real se añadira cuando integremos
        // la parte de sensores y comunicacion del proyecto

        Toast.makeText(
                this,
                "La conexión con la maleta todavía no está disponible",
                Toast.LENGTH_LONG
        ).show();

        // Mantengo el estado real: sin conectar
        tvEstadoConexion.setText("Sin conectar");
    }


    // =========================================
    // CERRAR SESION
    // =========================================

    private void cerrarSesion() {

        // Cierro la sesion actual de Firebase
        auth.signOut();

        Toast.makeText(
                this,
                "Sesión cerrada correctamente",
                Toast.LENGTH_SHORT
        ).show();

        // Vuelvo a la pantalla de inicio de sesion
        volverAlLogin();
    }


    // =========================================
    // VOLVER AL INICIO DE SESION
    // =========================================

    private void volverAlLogin() {

        Intent intent = new Intent(
                CuentaActivity.this,
                LoginActivity.class
        );

        // Limpio las pantallas anteriores para que
        // no se pueda volver a la cuenta con Atras
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}
