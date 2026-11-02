using System.Drawing;
using System.Drawing.Imaging;

namespace Surplus.Rete;

/// <summary>
/// Prepara una foto prima di spedirla al servizio AI: raddrizzata, rimpicciolita
/// e in JPEG. Stesse scelte dell'app Android (ImageForAi.kt), per due motivi
/// concreti: il costo di una chiamata cresce coi pixel, e una foto storta rende
/// illeggibile il testo di un'etichetta.
/// </summary>
public static class Immagini
{
    public const int LatoMax = 1536;
    private const int LatoAnteprima = 220;

    public sealed record Preparata(string Base64, string Tipo, int Larghezza, int Altezza, string Anteprima);

    public static Preparata? Prepara(string file)
    {
        try
        {
            using var originale = new Bitmap(file);
            using var dritta = Raddrizza(originale);
            using var scalata = Scala(dritta, LatoMax);
            using var piccola = Scala(dritta, LatoAnteprima);
            return new Preparata(
                Base64: Convert.ToBase64String(Jpeg(scalata, 85)),
                Tipo: "image/jpeg",
                Larghezza: scalata.Width,
                Altezza: scalata.Height,
                Anteprima: "data:image/jpeg;base64," + Convert.ToBase64String(Jpeg(piccola, 70)));
        }
        catch (Exception e) when (e is ArgumentException or OutOfMemoryException or IOException or System.Runtime.InteropServices.ExternalException)
        {
            Motore.Log.Errore("immagine non preparata: " + Path.GetFileName(file), e);
            return null;
        }
    }

    /// <summary>Costo indicativo in token immagine: larghezza × altezza / 750, come da documentazione.</summary>
    public static int TokenStimati(int larghezza, int altezza) => larghezza * altezza / 750;

    /// <summary>L'orientamento scritto dalla fotocamera (EXIF 0x0112): senza, le foto verticali arrivano coricate.</summary>
    private static Bitmap Raddrizza(Bitmap img)
    {
        const int Orientamento = 0x0112;
        if (!img.PropertyIdList.Contains(Orientamento)) return (Bitmap)img.Clone();
        var valore = BitConverter.ToUInt16(img.GetPropertyItem(Orientamento)!.Value!, 0);
        var giro = valore switch
        {
            3 => RotateFlipType.Rotate180FlipNone,
            6 => RotateFlipType.Rotate90FlipNone,
            8 => RotateFlipType.Rotate270FlipNone,
            _ => RotateFlipType.RotateNoneFlipNone,
        };
        var copia = (Bitmap)img.Clone();
        if (giro != RotateFlipType.RotateNoneFlipNone) copia.RotateFlip(giro);
        return copia;
    }

    private static Bitmap Scala(Bitmap img, int latoMax)
    {
        var lato = Math.Max(img.Width, img.Height);
        if (lato <= latoMax) return (Bitmap)img.Clone();
        var fattore = (double)latoMax / lato;
        var l = Math.Max(1, (int)Math.Round(img.Width * fattore));
        var a = Math.Max(1, (int)Math.Round(img.Height * fattore));
        var fuori = new Bitmap(l, a);
        using var g = Graphics.FromImage(fuori);
        g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
        g.DrawImage(img, 0, 0, l, a);
        return fuori;
    }

    private static byte[] Jpeg(Bitmap img, long qualità)
    {
        var codificatore = ImageCodecInfo.GetImageEncoders().First(c => c.FormatID == ImageFormat.Jpeg.Guid);
        using var parametri = new EncoderParameters(1);
        parametri.Param[0] = new EncoderParameter(Encoder.Quality, qualità);
        using var memoria = new MemoryStream();
        img.Save(memoria, codificatore, parametri);
        return memoria.ToArray();
    }
}
