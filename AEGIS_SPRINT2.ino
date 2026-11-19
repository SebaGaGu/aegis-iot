#include <M5Stack.h>
#include <SPI.h>
#include <SD.h>
#include <MFRC522.h>
#include <driver/i2s.h>
#include <string.h>

// ==================== RFID ====================
// Pin CS del RC522: permite seleccionar el lector en el bus SPI compartido.
#define SS_PIN 21
// Pin de reinicio del lector RFID.
#define RST_PIN 22
// Objeto que gestiona la comunicacion con el modulo RC522.
MFRC522 rfid(SS_PIN, RST_PIN);
// UID del llavero autorizado; se compara byte a byte al acercar una tarjeta.
const byte UID_AUTORIZADO[] = { 0x59, 0xE5, 0xDE, 0xA2 };
// Longitud esperada del UID para rechazar identificadores de otro tamano.
const byte UID_LONGITUD = sizeof(UID_AUTORIZADO);


// ==================== MICROSD Y AUDIO ====================
// Reproductor PCM WAV: mono, 16 kHz, 16 bits, sin librerias de audio externas.
// DAC interno de ESP32, canal derecho GPIO25 (altavoz M5Stack Basic).
// Chip select de la microSD integrada. Debe ser distinto del CS del RFID.
const int SD_CS = 4;
// Indica si el montaje de la tarjeta SD se ha realizado correctamente.
bool sdDisponible = false;
// Ruta del sonido solicitado; permite lanzarlo desde el bucle principal.
const char* audioPendiente = nullptr;
// Archivo WAV abierto actualmente en la microSD.
File archivoAudio;
// Permite saber si hay un WAV en curso y evitar consultas RFID durante su lectura.
bool reproduciendo = false;
// Numero de muestras PCM que quedan por enviar a la salida de audio.
uint32_t muestrasPendientes = 0;
// Registra si se ha podido inicializar la salida I2S/DAC.
bool audioI2SDisponible = false;

// Los WAV guardan sus valores numericos en formato little-endian.
// Esta funcion reconstruye un entero de 16 bits a partir de dos bytes.
uint16_t leer16(File& archivo) {
  uint16_t b0 = archivo.read();
  uint16_t b1 = archivo.read();
  return b0 | (b1 << 8);
}

// Lee cuatro bytes little-endian; se usa para longitudes y frecuencias del WAV.
uint32_t leer32(File& archivo) {
  uint32_t b0 = archivo.read();
  uint32_t b1 = archivo.read();
  uint32_t b2 = archivo.read();
  uint32_t b3 = archivo.read();
  return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
}

// Configura I2S para reproducir PCM por el DAC integrado del ESP32.
bool iniciarSalidaAudio() {
  i2s_config_t cfg = {};
  cfg.mode = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_TX | I2S_MODE_DAC_BUILT_IN);
  // Frecuencia fijada al formato exigido a los archivos WAV.
  cfg.sample_rate = 16000;
  cfg.bits_per_sample = I2S_BITS_PER_SAMPLE_16BIT;
  cfg.channel_format = I2S_CHANNEL_FMT_ONLY_RIGHT;
  cfg.communication_format = I2S_COMM_FORMAT_I2S_MSB;
  cfg.intr_alloc_flags = 0;
  // El DMA mantiene varios bloques en cola para alimentar el DAC.
  cfg.dma_buf_count = 4;
  cfg.dma_buf_len = 128;
  cfg.use_apll = false;
  cfg.tx_desc_auto_clear = true;

  // Si falla la instalacion del controlador, el audio se deshabilita.
  if (i2s_driver_install(I2S_NUM_0, &cfg, 0, nullptr) != ESP_OK) return false;
  // Comprueba tanto la asociacion de pines como la activacion del DAC.
  if (i2s_set_pin(I2S_NUM_0, nullptr) != ESP_OK ||
      i2s_set_dac_mode(I2S_DAC_CHANNEL_RIGHT_EN) != ESP_OK) {
    i2s_driver_uninstall(I2S_NUM_0);
    return false;
  }
  return true;
}

// Solicita una locucion sin abrir el archivo en mitad de la deteccion del evento.
void solicitarAudio(const char* nombre) {
  // Sin SD o sin I2S no se puede iniciar la reproduccion.
  if (!sdDisponible || !audioI2SDisponible) return;
  // Conservar solo el ultimo evento pendiente.
  audioPendiente = nombre;
}

// Limpia el estado de reproduccion y libera el archivo si estaba abierto.
void detenerAudio() {
  reproduciendo = false;
  muestrasPendientes = 0;
  if (archivoAudio) archivoAudio.close();
}

// Selecciona la microSD y comprueba la cabecera antes de reproducir.
bool abrirAudioWav(const char* ruta) {
  // El lector RFID debe quedar deseleccionado mientras accedemos a la SD.
  digitalWrite(SS_PIN, HIGH);
  archivoAudio = SD.open(ruta, FILE_READ);
  // Si el archivo no existe o no se puede abrir, se cancela el audio.
  if (!archivoAudio) return false;

  // Cada identificador RIFF/WAVE ocupa cuatro caracteres.
  char id[4];
  if (archivoAudio.readBytes(id, 4) != 4 || memcmp(id, "RIFF", 4) != 0) return false;
  leer32(archivoAudio);
  if (archivoAudio.readBytes(id, 4) != 4 || memcmp(id, "WAVE", 4) != 0) return false;

  // Solo aceptamos audio PCM, mono, 16000 Hz y 16 bits.
  bool formatoValido = false;
  // WAV puede contener otros chunks antes de los datos.
  // Recorre los bloques del WAV, cuyo encabezado ocupa al menos 8 bytes.
  while (archivoAudio.available() >= 8) {
    if (archivoAudio.readBytes(id, 4) != 4) break;
    uint32_t longitud = leer32(archivoAudio);
    uint32_t inicio = archivoAudio.position();
    // Descarta bloques que indican una longitud mayor que el archivo restante.
    if (longitud > archivoAudio.size() - inicio) break;

    // El bloque fmt contiene la codificacion y el formato de las muestras.
    if (memcmp(id, "fmt ", 4) == 0 && longitud >= 16) {
      uint16_t codificacion = leer16(archivoAudio);
      uint16_t canales = leer16(archivoAudio);
      uint32_t frecuencia = leer32(archivoAudio);
      leer32(archivoAudio);
      leer16(archivoAudio);
      uint16_t bits = leer16(archivoAudio);
      formatoValido = (codificacion == 1 && canales == 1 &&
                       frecuencia == 16000 && bits == 16);
    // El bloque data contiene las muestras que se enviaran al altavoz.
    } else if (memcmp(id, "data", 4) == 0) {
      // No se reproduce el archivo si su formato no ha sido validado.
      if (!formatoValido) return false;
      muestrasPendientes = longitud / 2;
      reproduciendo = muestrasPendientes > 0;
      return reproduciendo;
    }
    // El formato RIFF alinea los bloques en posiciones pares.
    uint32_t siguiente = inicio + longitud + (longitud & 1);
    if (siguiente > archivoAudio.size()) break;
    archivoAudio.seek(siguiente);
  }
  return false;
}

// Gestiona el audio por fragmentos para seguir atendiendo sensores en loop().
void servicioAudio() {
  // Sin SD o sin I2S no se puede iniciar la reproduccion.
  if (!sdDisponible || !audioI2SDisponible) return;

  // Una peticion nueva sustituye cualquier sonido que estuviera sonando.
  if (audioPendiente != nullptr) {
    const char* ruta = audioPendiente;
    audioPendiente = nullptr;
    detenerAudio();
    i2s_zero_dma_buffer(I2S_NUM_0);
    // Si falla la apertura o el formato, se informa por serie y se cierra.
    if (!abrirAudioWav(ruta)) {
      Serial.printf("No se pudo abrir WAV PCM mono 16 kHz/16 bits: %s\n", ruta);
      detenerAudio();
      return;
    }
    Serial.printf("Reproduciendo: %s\n", ruta);
  }

  // Sin un archivo activo no se realizan lecturas ni escrituras I2S.
  if (!reproduciendo) return;
  // 128 muestras = 8 ms. Evita bloquear el bucle durante un audio completo.
  uint16_t salida[128];
  // No se leen mas muestras de las que quedan en el archivo.
  size_t cantidad = muestrasPendientes < 128 ? muestrasPendientes : 128;
  // El lector RFID debe quedar deseleccionado mientras accedemos a la SD.
  digitalWrite(SS_PIN, HIGH);
  // Cada muestra PCM de 16 bits se lee como dos bytes consecutivos.
  for (size_t n = 0; n < cantidad; ++n) {
    int32_t b0 = archivoAudio.read();
    int32_t b1 = archivoAudio.read();
    // Detiene el bloque si el archivo termina antes de lo esperado.
    if (b0 < 0 || b1 < 0) {
      cantidad = n;
      muestrasPendientes = n;
      break;
    }
    int16_t pcm = (int16_t)((uint16_t)b0 | ((uint16_t)b1 << 8));
    // El DAC integrado necesita PCM sin signo en los 8 bits altos.
    uint16_t dac = (uint16_t)(((int32_t)pcm + 32768) >> 8);
    salida[n] = dac << 8;
  }
  size_t escritos = 0;
  // Solo se envia audio al controlador si se ha preparado algun dato.
  if (cantidad > 0) {
    // Comprueba si I2S acepta el bloque y detecta errores de escritura.
    if (i2s_write(I2S_NUM_0, salida, cantidad * sizeof(uint16_t),
                  &escritos, pdMS_TO_TICKS(20)) != ESP_OK) {
      Serial.println("ERROR: I2S no pudo escribir audio");
      detenerAudio();
      return;
    }
    muestrasPendientes -= escritos / sizeof(uint16_t);
  }
  // Al terminar, libera el archivo y deja disponible el sistema para otro audio.
  if (muestrasPendientes == 0) {
    detenerAudio();
    Serial.println("Audio enviado al DAC");
  }
}

// ==================== GOLPES ====================
// Entrada digital del sensor de inclinacion.
const int PIN_TILT = 16;
// Entrada digital del sensor de vibracion.
const int PIN_VIBRACION = 17;
// Nivel logico que interpretamos como activacion del TILT.
const int ACTIVO_TILT = HIGH;
// Nivel logico que interpretamos como activacion del sensor de vibracion.
const int ACTIVO_VIBRACION = HIGH;
// Tiempo minimo entre lecturas de los dos sensores, en milisegundos.
const unsigned long INTERVALO_LECTURA = 5;
// Margen para que TILT y vibracion se activen como parte del mismo golpe.
const unsigned long VENTANA_COINCIDENCIA = 450;
// Plazo maximo para acumular los impactos necesarios.
const unsigned long VENTANA_IMPACTOS = 3000;
// Evita contar rebotes demasiado seguidos como golpes independientes.
const unsigned long SEPARACION_IMPACTOS = 100;
// Pausa tras una alarma de golpe antes de aceptar otra secuencia.
const unsigned long COOLDOWN = 2000;
// Cantidad de impactos validos requerida para activar la alarma.
const int IMPACTOS_NECESARIOS = 2;

// ==================== LUZ ====================
// Entrada analogica conectada a la fotoresistencia.
const int PIN_LUZ = 35;
// Lectura a partir de la que se considera posible entrada de luz.
const int UMBRAL_ABIERTA = 3650;
// Umbral distinto al anterior para evitar oscilaciones cerca del limite.
const int UMBRAL_CERRADA = 3700;
// La nueva condicion de luz debe mantenerse un segundo antes de confirmarla.
const unsigned long TIEMPO_CONFIRMACION_LUZ = 1000;
// Periodicidad de la lectura analogica del LDR.
const unsigned long INTERVALO_LUZ = 50;
// Ultimo valor analogico leido del LDR.
int valorLuz = 0;
// Estado confirmado del sensor de luz tras el tiempo de estabilidad.
bool luzAbierta = false;
// Estado provisional pendiente de confirmacion.
bool candidatoLuz = false;
// Instante en que comenzo la posible variacion del estado de luz.
unsigned long inicioCambioLuz = 0;
// Marca temporal para limitar la frecuencia de muestreo del LDR.
unsigned long ultimaLecturaLuz = 0;
// Controla cada cuanto se imprimen las lecturas del LDR por serie.
unsigned long ultimoLogLuz = 0;

// ==================== SISTEMA ====================
// Tiempo de espera entre lecturas RFID aceptadas.
const unsigned long ESPERA_RFID = 2000;
// Indica si la maleta esta en modo vigilancia.
bool vigilanciaActiva = false;
// Marca una alarma de golpes en curso durante el periodo de espera.
bool alarmaActiva = false;
// Indica que el sensor de luz ha detectado una posible apertura.
bool alarmaApertura = false;
// Ultimo instante de comprobacion de los sensores de golpes.
unsigned long ultimaLectura = 0;
// Evita actualizar la pantalla en cada vuelta del programa.
unsigned long ultimaPantalla = 0;
// Sirve para evitar lecturas RFID repetidas demasiado pronto.
unsigned long ultimaLecturaRFID = 0;
// Momento de la ultima activacion nueva del TILT.
unsigned long ultimoTilt = 0;
// Momento de la ultima activacion nueva del sensor de vibracion.
unsigned long ultimaVibracion = 0;
// Inicio de la ventana de acumulacion de golpes.
unsigned long primerImpacto = 0;
// Momento del ultimo golpe valido, para filtrar rebotes.
unsigned long ultimoImpacto = 0;
// Momento de activacion de la alarma, utilizado para el cooldown.
unsigned long ultimoGolpe = 0;
// Contador de impactos validos dentro de la ventana temporal.
int impactos = 0;
// Lectura anterior del TILT; permite detectar el flanco de activacion.
bool anteriorTilt = false;
// Lectura anterior de vibracion para detectar una activacion nueva.
bool anteriorVibracion = false;
// Se ha detectado TILT y se espera coincidencia con vibracion.
bool pendienteTilt = false;
// Se ha detectado vibracion y se espera coincidencia con TILT.
bool pendienteVibracion = false;
// Instante hasta el que debe mostrarse un aviso temporal.
unsigned long mensajeHasta = 0;
// Texto de la ultima confirmacion o aviso transitorio.
String mensajeTemporal = "";

// Compara el UID detectado con el UID autorizado del sistema.
bool tarjetaAutorizada() {
  // La longitud debe coincidir antes de comparar sus bytes.
  if (rfid.uid.size != UID_LONGITUD) return false;
  for (byte i = 0; i < UID_LONGITUD; i++) {
    // Un solo byte distinto basta para denegar el acceso.
    if (rfid.uid.uidByte[i] != UID_AUTORIZADO[i]) return false;
  }
  return true;
}

// Restablece el contador y sincroniza las lecturas previas de los sensores.
void reiniciarGolpes() {
  impactos = 0;
  pendienteTilt = false;
  pendienteVibracion = false;
  primerImpacto = 0;
  ultimoImpacto = 0;
  alarmaActiva = false;
  anteriorTilt = digitalRead(PIN_TILT) == ACTIVO_TILT;
  anteriorVibracion = digitalRead(PIN_VIBRACION) == ACTIVO_VIBRACION;
}

// Actualiza unicamente las zonas de la pantalla cuyo contenido cambia.
void mostrarEstado() {
  // Con static se conserva el valor entre llamadas a mostrarEstado().
  static bool iniciada = false;
  // Evita redibujar el texto de vigilancia si no ha variado.
  static String estadoAnterior = "";
  // Recuerda el aviso mostrado para evitar parpadeos innecesarios.
  static String avisoAnterior = "";
  // Detecta una transicion entre vigilancia activa e inactiva.
  static bool vigilanciaAnterior = false;

  // Dibuja el encabezado y el pie solo en la primera llamada.
  if (!iniciada) {
    M5.Lcd.fillScreen(BLACK);
    M5.Lcd.setTextSize(3);
    M5.Lcd.setTextColor(CYAN);
    M5.Lcd.setCursor(15, 10);
    M5.Lcd.println("AEGIS");
    M5.Lcd.drawFastHLine(10, 45, 300, DARKGREY);
    M5.Lcd.setTextSize(1);
    M5.Lcd.setTextColor(LIGHTGREY);
    M5.Lcd.setCursor(15, 225);
    M5.Lcd.println("ACERCA TU LLAVERO :)");
    iniciada = true;
  }

  // Limpia la zona central al cambiar de modo para no dejar textos antiguos.
  if (vigilanciaActiva != vigilanciaAnterior) {
    M5.Lcd.fillRect(0, 50, 320, 165, BLACK);
    estadoAnterior = "";
    avisoAnterior = "";
    vigilanciaAnterior = vigilanciaActiva;
  }

  String estado = vigilanciaActiva ? "VIGILANCIA ACTIVA" : "VIGILANCIA OFF";
  // Solo actualiza la etiqueta del estado si ha cambiado.
  if (estado != estadoAnterior) {
    M5.Lcd.fillRect(10, 55, 300, 35, BLACK);
    M5.Lcd.setTextSize(2);
    M5.Lcd.setCursor(15, 60);
    M5.Lcd.setTextColor(vigilanciaActiva ? GREEN : RED);
    M5.Lcd.println(estado);
    estadoAnterior = estado;
  }

  // Vigilancia OFF: NO se muestran datos ni alertas de sensores.
  String aviso = "";
  uint16_t colorAviso = WHITE;
  // Mientras hay vigilancia, se muestran los eventos y el estado de monitorizacion.
  if (vigilanciaActiva) {
    aviso = "MONITORIZANDO...";
    colorAviso = CYAN;
    // Las posibles aperturas tienen prioridad visual sobre los golpes.
    if (alarmaApertura) {
      aviso = "APERTURA DETECTADA";
      colorAviso = RED;
    // Si no hay apertura, se muestra la alarma de golpe activa.
    } else if (alarmaActiva) {
      aviso = "GOLPE DETECTADO";
      colorAviso = RED;
    // Los avisos temporales caducan al llegar a mensajeHasta.
    } else if (mensajeTemporal != "" && (long)(mensajeHasta - millis()) > 0) {
      aviso = mensajeTemporal;
      colorAviso = YELLOW;
    }
  // En modo OFF solo se permite mostrar una denegacion temporal.
  } else if (mensajeTemporal == "ACCESO DENEGADO" &&
             (long)(mensajeHasta - millis()) > 0) {
    aviso = mensajeTemporal;
    colorAviso = RED;
  }

  // Redibuja el area de avisos solo si su contenido ha variado.
  if (aviso != avisoAnterior) {
    M5.Lcd.fillRect(10, 105, 300, 45, BLACK);
    if (aviso != "") {
      M5.Lcd.setTextSize(2);
      M5.Lcd.setCursor(15, 110);
      M5.Lcd.setTextColor(colorAviso);
      M5.Lcd.println(aviso);
    }
    avisoAnterior = aviso;
  }
}

// Revisa si hay una tarjeta y cambia el modo solo si su UID es correcto.
void comprobarRFID() {
  // No usar el lector SPI mientras se lee un WAV desde la microSD.
  // La SD y el RC522 comparten SPI; se evita consultar RFID durante el audio.
  if (reproduciendo || audioPendiente != nullptr) return;
  unsigned long ahora = millis();
  // Reduce relecturas consecutivas del mismo llavero.
  if (ultimaLecturaRFID != 0 && ahora - ultimaLecturaRFID < ESPERA_RFID) return;
  digitalWrite(SD_CS, HIGH);
  // Sin tarjeta cercana, la funcion termina sin modificar el estado.
  if (!rfid.PICC_IsNewCardPresent()) return;
  // Solo se comprueba la autorizacion cuando el UID se ha leido.
  if (!rfid.PICC_ReadCardSerial()) return;
  ultimaLecturaRFID = ahora;

  bool autorizada = tarjetaAutorizada();
  // El llavero correcto alterna entre vigilancia activada y desactivada.
  if (autorizada) {
    vigilanciaActiva = !vigilanciaActiva;
    reiniciarGolpes();
    alarmaApertura = vigilanciaActiva && luzAbierta;
    candidatoLuz = luzAbierta;
    inicioCambioLuz = ahora;

    // Al activar, se reproduce el audio de confirmacion correspondiente.
    if (vigilanciaActiva) {
      Serial.println("VIGILANCIA ACTIVADA");
      mensajeTemporal = "ACCESO PERMITIDO";
      digitalWrite(21, HIGH);
      solicitarAudio("/activado.wav");
    } else {
      Serial.println("VIGILANCIA DESACTIVADA");
      mensajeTemporal = "VIGILANCIA OFF";
      digitalWrite(21, HIGH);
      solicitarAudio("/desactivado.wav");
    }
  } else {
    Serial.println("ACCESO DENEGADO");
    mensajeTemporal = "ACCESO DENEGADO";
    digitalWrite(21, HIGH);
    solicitarAudio("/denegado.wav");
  }

  mensajeHasta = ahora + 1500;
  rfid.PICC_HaltA();
  rfid.PCD_StopCrypto1();
  mostrarEstado();
}

// Valida cada impacto combinando las señales de TILT y vibracion.
void comprobarGolpes() {
  unsigned long ahora = millis();
  // Respeta el intervalo minimo entre comprobaciones.
  if (ahora - ultimaLectura < INTERVALO_LECTURA) return;
  ultimaLectura = ahora;

  bool tilt = digitalRead(PIN_TILT) == ACTIVO_TILT;
  bool vibracion = digitalRead(PIN_VIBRACION) == ACTIVO_VIBRACION;
  // Cuenta el inicio de una activacion, no un sensor que sigue en HIGH.
  bool nuevoTilt = tilt && !anteriorTilt;
  // El mismo criterio de flanco se aplica al sensor de vibracion.
  bool nuevaVibracion = vibracion && !anteriorVibracion;
  anteriorTilt = tilt;
  anteriorVibracion = vibracion;
  // Los sensores se leen, pero no producen alarmas cuando la vigilancia esta OFF.
  if (!vigilanciaActiva) return;

  // Mientras dura el cooldown no se registran nuevos impactos.
  if (alarmaActiva) {
    // Una vez transcurrida la espera, prepara una nueva secuencia de golpes.
    if (ahora - ultimoGolpe >= COOLDOWN) reiniciarGolpes();
    return;
  }

  // Los golpes caducan si no se completa la secuencia a tiempo.
  if (impactos > 0 && ahora - primerImpacto > VENTANA_IMPACTOS) impactos = 0;
  // Guarda una posible mitad de un impacto valido.
  if (nuevoTilt) {
    ultimoTilt = ahora;
    pendienteTilt = true;
  }
  // Guarda la activacion de vibracion para comprobar coincidencia temporal.
  if (nuevaVibracion) {
    ultimaVibracion = ahora;
    pendienteVibracion = true;
  }
  // Descarta una activacion TILT sin pareja dentro de 450 ms.
  if (pendienteTilt && ahora - ultimoTilt > VENTANA_COINCIDENCIA) pendienteTilt = false;
  // Tambien caduca la espera de una senal de vibracion.
  if (pendienteVibracion && ahora - ultimaVibracion > VENTANA_COINCIDENCIA) pendienteVibracion = false;

  // Solo hay impacto valido si ambos sensores coinciden dentro del margen.
  if (pendienteTilt && pendienteVibracion) {
    // Exige una separacion minima para no acumular rebotes.
    if (ultimoImpacto == 0 || ahora - ultimoImpacto >= SEPARACION_IMPACTOS) {
      // El primer impacto inicia los tres segundos de acumulacion.
      if (impactos == 0) primerImpacto = ahora;
      impactos++;
      ultimoImpacto = ahora;
      Serial.printf("Impacto valido: %d/%d\n", impactos, IMPACTOS_NECESARIOS);
      // La alarma se activa al alcanzar los dos impactos configurados.
      if (impactos >= IMPACTOS_NECESARIOS) {
        alarmaActiva = true;
        ultimoGolpe = ahora;
        Serial.println("!!! GOLPE DETECTADO !!!");
        digitalWrite(21, HIGH);
        solicitarAudio("/golpe.wav");
      }
    }
    pendienteTilt = false;
    pendienteVibracion = false;
  }
}

// Determina posibles aperturas a partir de un cambio sostenido de iluminacion.
void comprobarLuz() {
  unsigned long ahora = millis();
  // Evita lecturas analogicas mas frecuentes de lo necesario.
  if (ahora - ultimaLecturaLuz < INTERVALO_LUZ) return;
  ultimaLecturaLuz = ahora;
  valorLuz = analogRead(PIN_LUZ);

  bool hayLuz = candidatoLuz;
  // Cuando baja del umbral se propone el estado de posible apertura.
  if (valorLuz <= UMBRAL_ABIERTA) hayLuz = true;
  // Cuando sube lo suficiente se propone volver al estado cerrado.
  else if (valorLuz >= UMBRAL_CERRADA) hayLuz = false;

  // Un cambio de candidato reinicia el tiempo de confirmacion.
  if (hayLuz != candidatoLuz) {
    candidatoLuz = hayLuz;
    inicioCambioLuz = ahora;
  }

  // Solo confirma cambios sostenidos; filtra variaciones breves de luz.
  if (candidatoLuz != luzAbierta &&
      ahora - inicioCambioLuz >= TIEMPO_CONFIRMACION_LUZ) {
    luzAbierta = candidatoLuz;
    // Si el cambio confirmado implica luz, se registra el evento.
    if (luzAbierta) {
      Serial.println("LUZ DETECTADA");
      if (vigilanciaActiva) {
        alarmaApertura = true;
        Serial.println("!!! APERTURA DETECTADA !!!");
        digitalWrite(21, HIGH);
        solicitarAudio("/apertura.wav");
      }
    } else {
      Serial.println("OSCURIDAD DETECTADA");
      alarmaApertura = false;
    }
  }
  // Las lecturas se imprimen cada medio segundo para no saturar el monitor.
  if (ahora - ultimoLogLuz >= 500) {
    ultimoLogLuz = ahora;
    Serial.printf("LDR: %d | Abierta: %d\n", valorLuz, luzAbierta);
  }
}

// Inicializa el equipo una sola vez al arrancar.
void setup() {
  M5.begin();
  Serial.begin(115200);

  pinMode(PIN_TILT, INPUT);
  pinMode(PIN_VIBRACION, INPUT);
  pinMode(PIN_LUZ, INPUT);
  analogReadResolution(12);

  // CS independientes en el bus SPI compartido.
  pinMode(SS_PIN, OUTPUT);
  // El lector RFID debe quedar deseleccionado mientras accedemos a la SD.
  digitalWrite(SS_PIN, HIGH);
  pinMode(SD_CS, OUTPUT);
  digitalWrite(SD_CS, HIGH);
  // Las dos periferias SPI usan SCK 18, MISO 19 y MOSI 23.
  SPI.begin(18, 19, 23, SD_CS);

  // Intenta montar la microSD a 10 MHz.
  sdDisponible = SD.begin(SD_CS, SPI, 10000000);
  Serial.println(sdDisponible ? "microSD lista" : "ERROR: microSD no disponible");
  // El resultado indica si pueden reproducirse locuciones.
  audioI2SDisponible = iniciarSalidaAudio();
  Serial.println(audioI2SDisponible ? "I2S DAC listo" : "ERROR: I2S DAC no disponible");

  digitalWrite(SD_CS, HIGH);
  // Inicializa el RC522 despues de montar la SD.
  rfid.PCD_Init();
  reiniciarGolpes();

  valorLuz = analogRead(PIN_LUZ);
  luzAbierta = valorLuz <= UMBRAL_ABIERTA;
  candidatoLuz = luzAbierta;
  inicioCambioLuz = millis();

  Serial.println("AEGIS INICIADO");
  Serial.println("VIGILANCIA DESACTIVADA");
  mostrarEstado();
  // El lector RFID debe quedar deseleccionado mientras accedemos a la SD.
  digitalWrite(SS_PIN, HIGH);
  solicitarAudio("/inicio.wav");
}

// Atiende RFID, sensores, sonido y pantalla de forma repetida.
void loop() {
  M5.update();
  comprobarRFID();
  comprobarGolpes();
  comprobarLuz();
  servicioAudio();
  // Limita la actualizacion visual para evitar redibujados continuos.
  if (millis() - ultimaPantalla >= 300) {
    ultimaPantalla = millis();
    mostrarEstado();
  }
}
