package lab.stoneshelter.services;

import java.net.InetAddress;
import java.net.UnknownHostException;
import lab.stoneshelter.exceptions.StoneChatUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StoneChatClientAddressService {
    private final StoneChatSettings settings;
    public StoneChatClientAddressService(StoneChatSettings settings) { this.settings = settings; }
    public String resolve(String peer, String forwarded) {
        String canonicalPeer = canonical(peer);
        boolean trusted = settings.proxies().stream().map(this::canonical).anyMatch(canonicalPeer::equals);
        return trusted ? canonical(forwarded) : canonicalPeer;
    }
    public String canonical(String address) {
        if (address != null && address.toLowerCase(java.util.Locale.ROOT).startsWith("::ffff:"))
            address = address.substring(7);
        if (address == null || !(address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")
                || address.matches("[0-9a-fA-F:]+") && address.contains(":")))
            throw new StoneChatUnavailableException(new IllegalArgumentException("Invalid literal client address"));
        if (address.contains(".")) for (String octet : address.split("\\."))
            if (Integer.parseInt(octet) > 255) throw new StoneChatUnavailableException();
        try { return InetAddress.getByName(address).getHostAddress(); }
        catch (UnknownHostException exception) { throw new StoneChatUnavailableException(exception); }
    }
}
