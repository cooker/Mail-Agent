package io.mailagent;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import java.util.*;

public final class AddressUtils {
    private AddressUtils() {}
    public static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
    public static Set<String> parse(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return result;
        try {
            for (InternetAddress address : InternetAddress.parse(text.replace(';', ',').replace('\n', ','), true)) {
                address.validate();
                String value = normalize(address.getAddress());
                if (address.isGroup() || !value.contains("@") || value.length() > 254 || value.contains("\r"))
                    throw new IllegalArgumentException();
                result.add(value);
            }
            return result;
        } catch (Exception e) { throw new IllegalArgumentException("邮箱地址无效，请用逗号或换行分隔完整邮箱地址"); }
    }
    public static Set<String> recipients(Message message) throws MessagingException {
        Set<String> result = new LinkedHashSet<>();
        add(result, message.getRecipients(Message.RecipientType.TO));
        add(result, message.getRecipients(Message.RecipientType.CC));
        return result;
    }
    private static void add(Set<String> result, Address[] addresses) throws MessagingException {
        if (addresses == null) return;
        for (Address address : addresses) {
            if (address instanceof InternetAddress ia) {
                if (ia.isGroup()) add(result, ia.getGroup(false));
                else result.add(normalize(ia.getAddress()));
            }
        }
    }
    public static String display(Address[] addresses) {
        return addresses == null ? "" : InternetAddress.toString(addresses);
    }
    public static String clip(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }
}
