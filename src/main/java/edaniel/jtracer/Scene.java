package edaniel.jtracer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Scene {

    private List<SceneObject> mObjects;

    public Scene() {
        mObjects = new ArrayList<>();

//        int size = 1000;
//
//        Random r = new Random(12345);
//        for (int i = 0; i < 2000; i++) {
//            Sphere sphere = new Sphere();
//            float x,y,z;
//            x = r.nextFloat() * size - size/2;
//            y = r.nextFloat() * size - size/2;
//            z = r.nextFloat() * size - size/2;
//            sphere.setPosition(x,y,z);
//            mObjects.add(sphere);
//        }


        //            mObjects.add(new Mesh("cube.obj"));
//        mObjects.add(new Sphere(20));
//        mObjects.add(new Sphere());
        try {
            mObjects.add(new Mesh("men.obj"));
            mObjects.add(new Mesh("porshe.obj"));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public List<SceneObject> getScene() {
        return mObjects;
    }

    public void addObject(Sphere sphere) {
        mObjects.add(sphere);
    }
}
