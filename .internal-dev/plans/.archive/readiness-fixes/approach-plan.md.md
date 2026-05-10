> You are tasked with developing and implementing the robust fixes for several issues blocking our library from alpha. A 
senior engineer has give a high level overview with technical specification and battle tested suggestions. This document 
covers all the issues that need address and it is your job to make a plan for each issues using a deep and through exploration
of our codes base. The goal of the planning agents is to amortize the thinking needed to implement these fixes some are high high level.


# Your workflow for the task
- For each of the issues launch a subagent is to make a direct well written and full detailed review the  relent code 
and amortize you thinking into detailed plan, each
  - Launch one agent for each issue in the source file
  - Output these plans to .internal-dev/plans/readiness-fixes/final-plans

- After all the subagents have completed and written their plans to the directory for each plan file
  - Launch an agent to implement each of the new final plan filesS
  - These agents should not run in parallel, each agent should wait on the other blocking agent
- Perform final validation via playwright for validating aagents in semi-real workloads
- 