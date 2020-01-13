package com.team871.util;

import net.sourceforge.barbecue.BarcodeException;
import net.sourceforge.barbecue.CompositeModule;
import net.sourceforge.barbecue.Module;
import net.sourceforge.barbecue.SeparatorModule;
import net.sourceforge.barbecue.linear.LinearBarcode;
import net.sourceforge.barbecue.linear.code39.ModuleFactory;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;

/**
 * This is a concrete implementation of the Code 39 barcode, AKA 3of9,
 * USD-3.
 *
 * @author <a href="mailto:opensource@ianbourke.com">Ian Bourke</a>
 */
public class Code39Barcode extends LinearBarcode {
    /**
     * A list of type identifiers for the Code39 barcode format
     */
    public static final String[] TYPES = new String[]{
            "Code39", "USD3", "3of9"
    };
    private final boolean requiresChecksum;

    /**
     * Constructs a basic mode Code 39 barcode with the specified data and an optional
     * checksum.
     *
     * @param data             The data to encode
     * @param requiresChecksum A flag indicating whether a checksum is required or not
     * @throws BarcodeException If the data to be encoded is invalid
     */
    public Code39Barcode(String data, boolean requiresChecksum) throws BarcodeException {
        this(data, requiresChecksum, false);
    }

    /**
     * Constructs an extended mode Code 39 barcode with the specified data and an optional
     * checksum. The extended mode encodes all 128 ASCII characters using two character pairs
     * from the basic Code 39 character set. Note that most barcode scanners will need to
     * be configured to accept extended Code 39.
     *
     * @param data             The data to encode
     * @param requiresChecksum A flag indicating whether a checksum is required or not
     * @param extendedMode     Puts the barcode into extended mode, where all 128 ASCII characters can be encoded
     * @throws BarcodeException If the data to be encoded is invalid
     */
    public Code39Barcode(String data, boolean requiresChecksum, boolean extendedMode) throws BarcodeException {
        super(extendedMode ? encodeExtendedChars(data) : validateBasicChars(data));
        this.requiresChecksum = requiresChecksum;
    }

    /**
     * Returns the encoded data for the barcode.
     *
     * @return An array of modules that represent the data as a barcode
     */
    protected Module[] encodeData() {
        List modules = new ArrayList();
        for (int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);
            modules.add(new SeparatorModule(1));
            Module module = ModuleFactory.getModule(String.valueOf(c));
            modules.add(module);
        }
        modules.add(new SeparatorModule(1));
        return (Module[]) modules.toArray(new Module[0]);
    }

    /**
     * Returns the checksum for the barcode, pre-encoded as a Module.
     *
     * @return Null if no checksum is required, a Mod-43 calculated checksum otherwise
     */
    protected Module calculateChecksum() {
        if (requiresChecksum) {
            int checkIndex = calculateMod43(data);
            CompositeModule compositeModule = new CompositeModule();
            compositeModule.add(ModuleFactory.getModuleForIndex(checkIndex));
            compositeModule.add(new SeparatorModule(1));
            return compositeModule;
        }
        return null;
    }

    /**
     * Returns the for the Mod-43 checkIndex for the barcode as an int
     *
     * @return Mod-43 checkIndex for the given data String
     */
    public static int calculateMod43(final String givenData) {
        int sum = 0;
        StringCharacterIterator iter = new StringCharacterIterator(givenData);
        for (char c = iter.first(); c != CharacterIterator.DONE; c = iter.next()) {
            sum += ModuleFactory.getIndex(String.valueOf(c));
        }
        int checkIndex = sum % 43;
        return checkIndex;
    }

    /**
     * Returns the pre-amble for the barcode.
     *
     * @return ModuleFactory.START_STOP
     */
    protected Module getPreAmble() {
        return ModuleFactory.START_STOP;
    }

    /**
     * Returns the post-amble for the barcode.
     *
     * @return ModuleFactory.START_STOP
     */
    protected Module getPostAmble() {
        return ModuleFactory.START_STOP;
    }

    private static String validateBasicChars(String data) throws BarcodeException {
        StringCharacterIterator iter = new StringCharacterIterator(data);
        for (char c = iter.first(); c != CharacterIterator.DONE; c = iter.next()) {
            if (!ModuleFactory.hasModule(String.valueOf(c), false)) {
                throw new BarcodeException("Illegal character - try using extended mode if you need "
                        + "to encode the full ASCII character set");
            }
        }
        return data;
    }

    private static String encodeExtendedChars(String data) {
        StringBuffer buf = new StringBuffer();
        StringCharacterIterator iter = new StringCharacterIterator(data);
        for (char c = iter.first(); c != CharacterIterator.DONE; c = iter.next()) {
            if (!ModuleFactory.hasModule(String.valueOf(c), true)) {
                buf.append(ModuleFactory.getExtendedCharacter(c));
            } else {
                buf.append(c);
            }
        }
        return buf.toString();
    }
}
