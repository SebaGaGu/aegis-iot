package com.example.aegis.deteccion_golpes;

import com.google.firebase.firestore.PropertyName;

public class Golpes {
    private String id;
    private double fuerza;
    private String fecha;
    private String hora;
    private double latitud;
    private double longitud;

    public Golpes() {
    }

    public Golpes(double fuerza, String fecha, String hora, double latitud, double longitud) {
        this.fuerza = fuerza;
        this.fecha = fecha;
        this.hora = hora;
        this.latitud = latitud;
        this.longitud = longitud;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @PropertyName("fuerza_golpe_recibido")
    public double getFuerza() {
        return fuerza;
    }

    @PropertyName("fuerza_golpe_recibido")
    public void setFuerza(Object fuerzaObj) {
        if (fuerzaObj instanceof Number) {
            this.fuerza = ((Number) fuerzaObj).doubleValue();
        } else if (fuerzaObj instanceof String) {
            try {
                this.fuerza = Double.parseDouble((String) fuerzaObj);
            } catch (Exception ignored) {
                this.fuerza = 0.0;
            }
        }
    }

    @PropertyName("golpe_recibido_fecha")
    public String getFecha() {
        return fecha;
    }

    @PropertyName("golpe_recibido_fecha")
    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    @PropertyName("golpe_recibido_hora")
    public String getHora() {
        return hora;
    }

    @PropertyName("golpe_recibido_hora")
    public void setHora(String hora) {
        this.hora = hora;
    }

    @PropertyName("latitud")
    public double getLatitud() {
        return latitud;
    }

    @PropertyName("latitud")
    public void setLatitud(Object latObj) {
        if (latObj instanceof Number) {
            this.latitud = ((Number) latObj).doubleValue();
        } else if (latObj instanceof String) {
            try {
                this.latitud = Double.parseDouble((String) latObj);
            } catch (Exception ignored) {
                this.latitud = 0.0;
            }
        }
    }

    @PropertyName("longitud")
    public double getLongitud() {
        return longitud;
    }

    @PropertyName("longitud")
    public void setLongitud(Object longObj) {
        if (longObj instanceof Number) {
            this.longitud = ((Number) longObj).doubleValue();
        } else if (longObj instanceof String) {
            try {
                this.longitud = Double.parseDouble((String) longObj);
            } catch (Exception ignored) {
                this.longitud = 0.0;
            }
        }
    }
}
