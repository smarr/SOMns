# Naming Conventions

## General Guidelines

SOMns and Newspeak generally are Smalltalks and thus, they follow for most cases
the Smalltalk naming conventions. This means, if there isn't a name for it in
Newspeak, check Pharo or Squeak.

For cases where Newspeak does not yet specify a clear candidate for a name,
or in cases where we might want to diverge for specific reason,
we generally should consider the naming used in related languages.

Specifically, we should consider these languages, in the order of preference:

1. Dart, most modern of the bunch
2. C#, had the chance to learn from Java's mistakes
3. Java, has names for many things we might need
4. JavaScript, got some modern concepts standardized where we might borrow names to be understandable by the mainstream

SOMns as a whole should still strive for a consistent Smalltalky style.
Not to please the Smalltalk gods, but to ensure the code is somewhat legible
and self-consistent.

## Errors vs. Exceptions

The design of the exception hierarchy follows somewhat established norms to
broadly categories exceptions into two groups:

- usage errors, i.e., errors based on incorrect API usage,
  user input processing, or other general programming errors

- program errors, i.e., exceptional cases that can't be prevented entirely

For usage errors, we use the term `Error` as part of class names.
Following Java, the general intuition is that an error is a serious problem
that normally can't be handled by an application, but needs to be addressed by
the programmer. This includes also system failures at run time, such as
allocation failures or stack overflows.

For program errors, we use the term `Exception` as part of the class name.
Exceptions are used to indicate rare special cases that an application wants to
handle, for instance failing to open a file, division by zero, etc.

> Disclaimer: This convention is not yet consistently applied to SOMns. (SM, 2018-02-01)
