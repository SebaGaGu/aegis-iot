package com.example.aegis.deteccion_golpes;

public class Golpes {
    private String id;
    private double fuerza;
    private String fecha;
    private String hora;
    private double latitud;
    private double longitud;

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

    public double getFuerza() {
        return fuerza;
    }

    public String getFecha() {
        return fecha;
    }

    public String getHora() {
        return hora;
    }

    public double getLatitud() {
        return latitud;
    }

    public double getLongitud() {
        return longitud;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setFuerza(double fuerza) {
        this.fuerza = fuerza;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public void setHora(String hora) {
        this.hora = hora;
    }

    public void setLatitud(double latitud) {
        this.latitud = latitud;
    }

    public void setLongitud(double longitud) {
        this.longitud = longitud;
    }
}
