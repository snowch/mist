package mist.api;

import mist.api.jdsl.RetVal;
import mist.api.jdsl.RetVals;
import mist.api.jdsl.RetVals$;

public class RetValues {

    private static RetVals instance = RetVals$.MODULE$;

    public static RetVal<Integer> of(int i) {
        return instance.intRetVal(i);
    }

    public RetVal<String> of(String s) {
        return instance.stringRetVal(s);
    }

}