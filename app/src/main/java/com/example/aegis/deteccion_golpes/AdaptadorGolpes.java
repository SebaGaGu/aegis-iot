package com.example.aegis.deteccion_golpes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aegis.R;

import java.util.List;

public class AdaptadorGolpes extends RecyclerView.Adapter<AdaptadorGolpes.ViewHolder> {

    protected List<Golpes> listaGolpes; // Lista de golpes a mostrar

    public AdaptadorGolpes(List<Golpes> listaGolpes) {
        this.listaGolpes = listaGolpes;
    }

    // Viewholder
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView fecha, hora;
        public ImageView foto;
        public Button btnMasInformacion;

        public ViewHolder(View itemView) {
            super(itemView);
            foto = itemView.findViewById(R.id.imageView);
            fecha = itemView.findViewById(R.id.textView3);
            hora = itemView.findViewById(R.id.textView4);
            btnMasInformacion = itemView.findViewById(R.id.button2);
        }

        // Personalizar el ViewHolder
        public void personaliza(Golpes golpe) {
            fecha.setText("Fecha: " + golpe.getFecha());
            hora.setText("Hora: " + golpe.getHora());
            foto.setImageResource(R.drawable.maleta_verde);
            foto.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
    }

    // Creamos el ViewHolder inflando
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.golpes_recibidos_elementos, parent, false);
        return new ViewHolder(view);
    }

    // Obtenemos el elemento según la posición y personalizamos el ViewHolder
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int posicion) {
        Golpes golpe = listaGolpes.get(posicion);
        holder.personaliza(golpe);
    }

    // Indicamos el número de elementos en la lista
    @Override
    public int getItemCount() {
        return listaGolpes != null ? listaGolpes.size() : 0;
    }
}
