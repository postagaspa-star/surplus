# Regole R8 per la build di release (offuscamento + rimozione codice inutile).
#
# Obiettivo: rendere poco leggibile l'APK una volta decompilato, SENZA rompere
# nulla. I due punti delicati sono la lettura del file dati e lo scanner.

# --- Tracce leggibili negli errori --------------------------------------------
# Senza queste, uno stack trace diventa illeggibile anche per me.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# --- Dati salvati: NON toccare i nomi degli enum ------------------------------
# I dati sul telefono sono JSON e contengono i nomi delle costanti degli enum
# ("COLAZIONE", "SPINTA", "FRUTTA"...). Se R8 li rinominasse, l'app non
# riuscirebbe più a rileggere il diario già salvato: perdita di dati reale.
-keepclassmembers enum * { *; }

# --- kotlinx.serialization ----------------------------------------------------
# I serializzatori generati devono sopravvivere insieme alle classi dei dati.
-keepclassmembers class it.gaspa.surplus.data.** {
    *** Companion;
}
-keepclasseswithmembers class it.gaspa.surplus.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class it.gaspa.surplus.data.**$$serializer { *; }
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.**

# --- Lettore di codici a barre ------------------------------------------------
# ZXing avvia una sua Activity e usa risorse proprie: lo lascio intatto perché
# un errore qui si vedrebbe solo con la fotocamera in mano, non nei test.
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }

# --- Componenti dichiarati nel manifest ---------------------------------------
# Android li istanzia per nome: vanno tenuti così come sono.
-keep class it.gaspa.surplus.SurplusApp { *; }
-keep class it.gaspa.surplus.MainActivity { *; }
-keep class it.gaspa.surplus.notify.ReminderReceiver { *; }
-keep class it.gaspa.surplus.notify.BootReceiver { *; }
