/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package uno.anahata.gemini.functions;

import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author pablo
 */
public class MultiPartResponse {
    public Object responseValue;
    public List<Part> parts = new ArrayList<>();

    @Override
    public String toString() {
        return "MultiPartResponse{" + "responseValue=" + responseValue + ", parts=" + parts.size() + '}';
    }
    
}
