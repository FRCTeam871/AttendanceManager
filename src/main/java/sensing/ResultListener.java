package sensing;

import com.google.zxing.Result;

public interface ResultListener {

    void changed(Result result);

}
