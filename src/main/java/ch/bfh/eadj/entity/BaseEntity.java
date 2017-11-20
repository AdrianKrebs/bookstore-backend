package ch.bfh.eadj.entity;

import javax.persistence.*;

@MappedSuperclass
public abstract class BaseEntity {

    //TODO warum diese strategy?
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long nr;

    @Version
    private int version;

    public Long getNr() {
        return nr;
    }

    public int getVersion() {
        return version;
    }


}
