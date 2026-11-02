using System.Security.Cryptography;
using System.Text;

namespace Surplus.Sistema;

/// <summary>
/// La chiave del servizio AI è dell'utente e non deve restare in chiaro in un file.
/// Windows la cifra legandola all'account: un altro utente dello stesso computer,
/// o lo stesso file copiato altrove, non la riapre.
/// </summary>
public static class Dpapi
{
    private static readonly byte[] Sale = Encoding.UTF8.GetBytes("Surplus.chiave.v1");

    public static string? Cifra(string? chiaro)
    {
        if (string.IsNullOrEmpty(chiaro)) return null;
        try
        {
            var dati = ProtectedData.Protect(Encoding.UTF8.GetBytes(chiaro), Sale, DataProtectionScope.CurrentUser);
            return Convert.ToBase64String(dati);
        }
        catch (CryptographicException)
        {
            return null;
        }
    }

    public static string? Decifra(string? cifrato)
    {
        if (string.IsNullOrEmpty(cifrato)) return null;
        try
        {
            var dati = ProtectedData.Unprotect(Convert.FromBase64String(cifrato), Sale, DataProtectionScope.CurrentUser);
            return Encoding.UTF8.GetString(dati);
        }
        catch (Exception e) when (e is CryptographicException or FormatException)
        {
            return null;
        }
    }
}
