import com.google.zxing.BinaryBitmap;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.oned.MultiFormatOneDReader;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

public class BarcodeReader {
    private static MultiFormatOneDReader reader;

    public static void main(String[] args) {
        reader = new MultiFormatOneDReader(null);
        try {
            Result r = reader.decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(ImageIO.read(new File(args[0]))))));
            System.out.println(r.toString());
        } catch (NotFoundException e) {
            e.printStackTrace();
        } catch (FormatException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
