package sensing;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.oned.OneDReader;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class Sense {

    Result cachedResult;
    Result instantResult;
    final OneDReader reader;
    List<ResultListener> listeners;

    public Sense(OneDReader reader) {
        this.reader = reader;
        this.listeners = new ArrayList<>();
    }

    public void update(BufferedImage input){
        instantResult = null;
        try {
            BinaryBitmap bbm = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(input)));
            Result newResult = reader.decode(bbm);
            Result oldResult = cachedResult;

            cachedResult = newResult;

            if(oldResult == null || !newResult.getText().equals(oldResult.getText())){
                for(ResultListener l : listeners) l.changed(newResult);
            }

            instantResult = cachedResult;
        } catch (NotFoundException e) {
            // should i log something?
        } catch (FormatException e){
            e.printStackTrace();
        }
    }

    public Result getInstantResult(){
        return instantResult;
    }

    public Result getCachedResult(){
        return cachedResult;
    }

    public boolean addListener(ResultListener listener){
        return listeners.add(listener);
    }

    public boolean removeListener(ResultListener listener){
        return listeners.remove(listener);
    }

}
