package sensing;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.oned.OneDReader;
import ui.imageprovider.ImageProvider;

import java.awt.image.BufferedImage;
import java.util.ArrayList;

public class ImageSense extends GenericSense {

    final OneDReader reader;
    ImageProvider imgProvider;

    public ImageSense(OneDReader reader, ImageProvider provider) {
        super();
        this.reader = reader;
        this.listeners = new ArrayList<>();
        this.imgProvider = provider;
    }

    @Override
    protected BarcodeResult findResult() {
        try {
            BinaryBitmap bbm = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(imgProvider.getImage())));
            Result newResult = reader.decode(bbm);
            return new BarcodeResult(newResult.getText(), System.currentTimeMillis());
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

}
