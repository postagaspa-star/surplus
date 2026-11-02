using System.Runtime.InteropServices;

namespace Surplus.Sistema;

/// <summary>
/// La barra del titolo scura: la finestra è la cornice di un telefono, con una
/// barra bianca sopra sembrerebbe un pezzo attaccato male.
/// </summary>
public static class Tema
{
    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr finestra, int attributo, ref int valore, int dimensione);

    private const int UsaModoScuro = 20;        // DWMWA_USE_IMMERSIVE_DARK_MODE (Windows 10 20H1 e successivi)
    private const int UsaModoScuroVecchio = 19; // lo stesso, sulle versioni precedenti

    public static void BarraDelTitolo(IntPtr finestra, bool scuro)
    {
        if (finestra == IntPtr.Zero) return;
        var valore = scuro ? 1 : 0;
        try
        {
            if (DwmSetWindowAttribute(finestra, UsaModoScuro, ref valore, sizeof(int)) != 0)
                DwmSetWindowAttribute(finestra, UsaModoScuroVecchio, ref valore, sizeof(int));
        }
        catch (Exception e) when (e is DllNotFoundException or EntryPointNotFoundException)
        {
        }
    }
}
