package dev.axonia.access;

/** Who holds the Owner role when an Organization is founded: one person, or everyone in one directory group. */
public sealed interface OwnerBinding {

    String subjectType();

    String subjectId();

    String label();

    record User(String subject, String label) implements OwnerBinding {

        @Override
        public String subjectType() {

            return "USER";
        }

        @Override
        public String subjectId() {

            return subject;
        }
    }

    record Group(String groupKey, String label) implements OwnerBinding {

        @Override
        public String subjectType() {

            return "GROUP";
        }

        @Override
        public String subjectId() {

            return groupKey;
        }
    }
}
