package br.com.api.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "TB_EMPLOYEE")
public class Employee extends User{

    @ManyToOne
    @JoinColumn(name = "client_id")
    private Client client;
}