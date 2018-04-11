package utils;

import java.io.Serializable;

public class Triple<X, Y,Z> implements Serializable{

	public  X x;
	public  Y y;
	public  Z z;

	  public Triple(X x, Y y,Z z) {
	    this.setX(x);
	    this.setY(y);
	    this.setZ(z);
	  }

    public X getX() {
        return x;
    }

    public void setX(X x) {
        this.x = x;
    }

    public Y getY() {
        return y;
    }

    public void setY(Y y) {
        this.y = y;
    }

    public Z getZ() {
        return z;
    }

    public void setZ(Z z) {
        this.z = z;
    }
}
