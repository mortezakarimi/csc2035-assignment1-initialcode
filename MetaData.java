import java.io.Serializable;

public class MetaData implements Serializable {

	private static final long serialVersionUID = 1L;
	private String name;   // name of the file to create on server
	private int size;       // size of the file to send
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getSize() {
		return size;
	}
	public void setSize(int size) {
		this.size = size;
	}  

}
