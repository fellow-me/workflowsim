package com.qiujie.entity;

import lombok.Data;
import org.cloudbus.cloudsim.Host;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class File {

    private static final AtomicInteger nextId = new AtomicInteger(0);


    private int id;
    private String name;
    private double size;

    // the location of the local file
    private Host host;

    public File(String name, double size) {
        this.id = nextId.getAndIncrement();
        this.name = name;
        this.size = size;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        File file = (File) o;
        return Objects.equals(id, file.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }


}
