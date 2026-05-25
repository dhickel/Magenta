- Have the agent be able to call a chron like tool for waiting
    - Can update the main agent thread of state of long running sub agents.
- Use git commits as inputs and outputs for validation hand offs
- To allow for sub plans, we need to map our plans to some way to reference each other, we currently keep them in the db
   it would be easier to reference on disk
- We should consider moving many things out of the database, and just use it for auditing and persistent context storage
  - Have a System for handling leases and locks on files, validation of schema and things like that