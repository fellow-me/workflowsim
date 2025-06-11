package com.qiujie.config;

import lombok.Data;

import java.util.List;


@Data
public class VmConfig {
    private String name;
    private int pes;
    private double mips;
    private double frequency;
    private List<FvConfig> fvConfigList;
}
