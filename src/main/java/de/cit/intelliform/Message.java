package de.cit.intelliform;

import java.io.Serializable;
import java.util.Objects;

public record Message(String message) implements Serializable {

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Message message1)) return false;
        return Objects.equals(message, message1.message);
    }

    @Override
    public String toString() {
        return "Message{" + "message='" + message + '\'' + '}';
    }
}
